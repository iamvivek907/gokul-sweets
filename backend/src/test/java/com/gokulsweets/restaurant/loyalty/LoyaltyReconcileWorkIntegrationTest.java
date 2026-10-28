package com.gokulsweets.restaurant.loyalty;

import static org.assertj.core.api.Assertions.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql:.*")
class LoyaltyReconcileWorkIntegrationTest {
    private JdbcTemplate jdbc, root;
    private String schema;
    private TransactionTemplate tx;

    @BeforeEach
    void setup() throws Exception {
        String url = System.getenv("DATABASE_URL"),
                user = System.getenv("DATABASE_USERNAME"),
                password = System.getenv("DATABASE_PASSWORD");
        root = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        schema = "loyalty_queue_" + UUID.randomUUID().toString().replace("-", "");
        root.execute("CREATE SCHEMA " + schema);
        var source =
                new DriverManagerDataSource(
                        url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                        user,
                        password);
        jdbc = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.execute(
                "CREATE TABLE orders(id bigint PRIMARY KEY,loyalty_enrolled boolean NOT NULL"
                        + " DEFAULT false,order_status text)");
        jdbc.execute(
                "CREATE TABLE payments(id bigint PRIMARY KEY,order_id bigint REFERENCES"
                        + " orders(id),payment_status text)");
        jdbc.execute(
                "CREATE TABLE verified_order_ownership(order_id bigint PRIMARY KEY REFERENCES"
                        + " orders(id))");
        jdbc.execute(
                "CREATE TABLE loyalty_holds(order_id bigint REFERENCES orders(id),state text)");
        jdbc.execute("CREATE TABLE loyalty_account_ledger(order_id bigint,kind text)");
        jdbc.execute(
                Files.readString(
                        Path.of("src/main/resources/db/migration/V142__loyalty_pending_work.sql")));
    }

    @AfterEach
    void cleanup() {
        if (root != null && schema != null) root.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    private LoyaltyReconcileWork worker() {
        return new LoyaltyReconcileWork(jdbc, new SimpleMeterRegistry());
    }

    private void enrolled(long id) {
        jdbc.update("INSERT INTO orders VALUES(?,true,'PENDING_PAYMENT')", id);
    }

    @Test
    void commitAndRollbackRecordExactlyTheOrdersThatChanged() {
        tx.executeWithoutResult(
                s -> {
                    enrolled(1);
                    s.setRollbackOnly();
                });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loyalty_reconcile_work", Long.class))
                .isZero();
        enrolled(2);
        jdbc.update("INSERT INTO payments VALUES(1,2,'PAID')");
        jdbc.update("INSERT INTO verified_order_ownership VALUES(2)");
        jdbc.update("UPDATE payments SET payment_status='PAID' WHERE id=1");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT version FROM loyalty_reconcile_work WHERE order_id=2",
                                Long.class))
                .isEqualTo(3);
    }

    @Test
    void guestOrdersDoNotCreateWorkButExistingHoldsAreProtected() {
        jdbc.update("INSERT INTO orders VALUES(1,false,'PENDING_PAYMENT')");
        assertThat(worker().claim()).isEmpty();
        jdbc.update("INSERT INTO loyalty_holds VALUES(1,'RESERVED')");
        assertThat(worker().claim()).hasSize(1);
    }

    @Test
    void newerEventsSurviveOldAcknowledgementsAndAreAvailableForRetry() {
        enrolled(1);
        var w = worker();
        var old = w.claim().getFirst();
        jdbc.update("UPDATE orders SET order_status='PICKED_UP' WHERE id=1");
        tx.executeWithoutResult(s -> w.complete(old));
        var current = worker().claim();
        assertThat(current).hasSize(1);
        assertThat(current.getFirst().version()).isGreaterThan(old.version());
        tx.executeWithoutResult(s -> w.complete(current.getFirst()));
        assertThat(worker().claim()).isEmpty();
    }

    @Test
    void twoWorkersCannotClaimTheSameLeaseAndExpiredLeasesRecover() {
        enrolled(1);
        var first = worker().claim().getFirst();
        assertThat(worker().claim()).isEmpty();
        jdbc.update(
                "UPDATE loyalty_reconcile_work SET lease_until=CURRENT_TIMESTAMP-INTERVAL '1"
                        + " second'");
        var replacement = worker().claim().getFirst();
        assertThat(replacement.lease()).isNotEqualTo(first.lease());
        worker().complete(first);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loyalty_reconcile_work", Long.class))
                .isEqualTo(1);
    }

    @Test
    void failuresBackOffWithoutDroppingWorkOrDelayingNewerEvents() {
        enrolled(1);
        var w = worker();
        var old = w.claim().getFirst();
        w.failed(old);
        assertThat(worker().claim()).isEmpty();
        jdbc.update("UPDATE orders SET order_status='CANCELLED' WHERE id=1");
        assertThat(worker().claim()).hasSize(1);
    }

    @Test
    void repairScansBoundedPagesAndWrapsWithoutLosingExistingQueueRows() {
        jdbc.execute("INSERT INTO orders SELECT n,true,'PICKED_UP' FROM generate_series(1,1005) n");
        jdbc.execute("INSERT INTO verified_order_ownership SELECT id FROM orders");
        jdbc.execute("INSERT INTO payments SELECT id,id,'PAID' FROM orders");
        jdbc.execute("DELETE FROM loyalty_reconcile_work");
        var w = worker();
        tx.executeWithoutResult(s -> w.repair());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loyalty_reconcile_work", Long.class))
                .isEqualTo(1000);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT last_order_id FROM loyalty_repair_cursor", Long.class))
                .isEqualTo(1000);
        tx.executeWithoutResult(s -> w.repair());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loyalty_reconcile_work", Long.class))
                .isEqualTo(1005);
        tx.executeWithoutResult(s -> w.repair());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT last_order_id FROM loyalty_repair_cursor", Long.class))
                .isZero();
    }

    @Test
    void pendingWorkIsDeletedWithItsOrder() {
        enrolled(1);
        jdbc.update("DELETE FROM orders WHERE id=1");
        assertThat(worker().claim()).isEmpty();
    }

    @Test
    void upgradeBackfillFindsExistingWorkWithoutReplayingSettledRewards() throws Exception {
        jdbc.execute(
                "INSERT INTO orders VALUES"
                    + " (1,true,'PICKED_UP'),(2,true,'PICKED_UP'),(3,false,'CANCELLED'),(4,false,'PICKED_UP'),(5,true,'CANCELLED'),(6,true,'CANCELLED')");
        jdbc.execute("INSERT INTO verified_order_ownership SELECT id FROM orders");
        jdbc.execute("INSERT INTO payments VALUES (1,1,'PAID'),(2,2,'PAID'),(4,4,'PAID')");
        jdbc.execute("INSERT INTO loyalty_holds VALUES (3,'RESERVED')");
        jdbc.execute(
                "INSERT INTO loyalty_account_ledger VALUES"
                        + " (1,'EARNED'),(5,'EARNED'),(6,'EARNED'),(6,'REVERSED')");
        jdbc.execute("DELETE FROM loyalty_reconcile_work");
        String migration =
                Files.readString(
                        Path.of(
                                "src/main/resources/db/migration/V143__loyalty_pending_work_backfill.sql"));
        jdbc.execute(migration);
        assertThat(
                        jdbc.queryForList(
                                "SELECT order_id FROM loyalty_reconcile_work ORDER BY order_id",
                                Long.class))
                .containsExactly(2L, 3L, 5L);
    }
}
