package com.gokulsweets.restaurant.order.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.UUID;

/** Checks real trigger visibility, including direct SQL, deletes and rolled-back writes. */
@SpringBootTest
class MenuInventoryVersionIntegrationTest {
    @Autowired MenuInventoryVersion versions;
    @Autowired VersionedMenuPreview previews;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    CartAvailabilityService service;

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    long branch, category, product, bp, allocation, slot;
    final LocalDate date = LocalDate.of(2026, 10, 22);

    @BeforeEach
    void setup() {
        String code = "VERSION-" + UUID.randomUUID();
        branch =
                jdbc.queryForObject(
                        "INSERT INTO branches(code,name) VALUES(?,'Version test') RETURNING id",
                        Long.class,
                        code);
        category =
                jdbc.queryForObject(
                        "INSERT INTO categories(code,name) VALUES(?,'Version sweets') RETURNING id",
                        Long.class,
                        code);
        product =
                jdbc.queryForObject(
                        "INSERT INTO products(code,name,category_id,base_price) VALUES(?,'Version"
                                + " sweet',?,40) RETURNING id",
                        Long.class,
                        code,
                        category);
        bp =
                jdbc.queryForObject(
                        "INSERT INTO branch_products(branch_id,product_id) VALUES(?,?) RETURNING"
                                + " id",
                        Long.class,
                        branch,
                        product);
        allocation =
                jdbc.queryForObject(
                        """
INSERT INTO inventory_daily_allocations(branch_product_id,service_date,status,inventory_unit,
  approved_quantity,ready_quantity,created_at,updated_at)
VALUES(?,?,'READY','PIECE',10,10,now(),now()) RETURNING id
""",
                        Long.class,
                        bp,
                        date);
        slot =
                jdbc.queryForObject(
                        "INSERT INTO pickup_slots(branch_id,slot_date,start_time,end_time,capacity)"
                                + " VALUES(?,?,'12:00','12:30',10) RETURNING id",
                        Long.class,
                        branch,
                        date);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM pickup_slots WHERE branch_id=?", branch);
        jdbc.update("DELETE FROM branch_pickup_settings WHERE branch_id=?", branch);
        jdbc.update("DELETE FROM inventory_daily_allocations WHERE branch_product_id=?", bp);
        jdbc.update("DELETE FROM branch_inventory_policies WHERE branch_product_id=?", bp);
        jdbc.update("DELETE FROM branch_products WHERE id=?", bp);
        jdbc.update("DELETE FROM products WHERE id=?", product);
        jdbc.update("DELETE FROM categories WHERE id=?", category);
        jdbc.update("DELETE FROM branches WHERE id=?", branch);
    }

    @Test
    void directStockAndCapacityWritesChangeTokensWithoutTouchingTimestamps() {
        var before = versions.read(branch, date);
        assertThat(versions.read(branch, date)).isEqualTo(before);
        jdbc.update(
                "UPDATE inventory_daily_allocations SET held_quantity=1 WHERE id=?", allocation);
        var held = versions.read(branch, date);
        assertThat(held).isNotEqualTo(before);
        jdbc.update(
                "UPDATE inventory_daily_allocations SET held_quantity=0,committed_quantity=1 WHERE"
                        + " id=?",
                allocation);
        var committed = versions.read(branch, date);
        assertThat(committed).isNotEqualTo(held);
        jdbc.update("UPDATE pickup_slots SET booked_count=1 WHERE id=?", slot);
        assertThat(versions.read(branch, date)).isNotEqualTo(committed);
    }

    @Test
    void insertDeleteAndDateScopingCannotHideChanges() {
        var nextDay = versions.read(branch, date.plusDays(1));
        var before = versions.read(branch, date);
        jdbc.update("DELETE FROM inventory_daily_allocations WHERE id=?", allocation);
        var deleted = versions.read(branch, date);
        assertThat(deleted).isNotEqualTo(before);
        assertThat(versions.read(branch, date.plusDays(1))).isEqualTo(nextDay);
        jdbc.update("DELETE FROM pickup_slots WHERE id=?", slot);
        assertThat(versions.read(branch, date)).isNotEqualTo(deleted);
        jdbc.update(
                "INSERT INTO pickup_slots(branch_id,slot_date,start_time,end_time,capacity)"
                        + " VALUES(?,?,'12:00','12:30',10)",
                branch,
                date);
        assertThat(versions.read(branch, date)).isNotEqualTo(deleted);
    }

    @Test
    void rolledBackStockAndSettingsEditsDoNotPublishAChange() {
        var before = versions.read(branch, date);
        new TransactionTemplate(manager)
                .executeWithoutResult(
                        status -> {
                            jdbc.update(
                                    "UPDATE inventory_daily_allocations SET held_quantity=2 WHERE"
                                            + " id=?",
                                    allocation);
                            jdbc.update(
                                    "INSERT INTO branch_pickup_settings(branch_id) VALUES(?)",
                                    branch);
                            assertThat(versions.read(branch, date)).isNotEqualTo(before);
                            status.setRollbackOnly();
                        });
        assertThat(versions.read(branch, date)).isEqualTo(before);
        jdbc.update("INSERT INTO branch_pickup_settings(branch_id) VALUES(?)", branch);
        assertThat(versions.read(branch, date)).isNotEqualTo(before);
    }

    @Test
    void policyAndBranchClosureInvalidatePreviews() {
        var before = versions.read(branch, date);
        jdbc.update(
                """
INSERT INTO branch_inventory_policies(branch_product_id,control_mode,inventory_unit,created_at,updated_at)
VALUES(?,'READY_STOCK','PIECE',now(),now())
""",
                bp);
        var policy = versions.read(branch, date);
        assertThat(policy).isNotEqualTo(before);
        jdbc.update(
                "UPDATE branch_inventory_policies SET online_enabled=true WHERE"
                        + " branch_product_id=?",
                bp);
        var enabled = versions.read(branch, date);
        assertThat(enabled).isNotEqualTo(policy);
        jdbc.update("UPDATE branches SET operational=false WHERE id=?", branch);
        assertThat(versions.read(branch, date)).isNotEqualTo(enabled);
    }

    @Test
    void concurrentCommitCannotRelabelAnOlderStockSnapshot() throws Exception {
        var seen = new java.util.ArrayList<Integer>();
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            org.mockito.Mockito.when(
                            service.check(
                                    org.mockito.ArgumentMatchers.eq(branch),
                                    org.mockito.ArgumentMatchers.eq(date),
                                    org.mockito.ArgumentMatchers.eq(1),
                                    org.mockito.ArgumentMatchers.anyList(),
                                    org.mockito.ArgumentMatchers.eq(true)))
                    .thenAnswer(
                            call -> {
                                assertThat(
                                                jdbc.queryForObject(
                                                        "SHOW transaction_isolation", String.class))
                                        .isEqualTo("repeatable read");
                                int before =
                                        jdbc.queryForObject(
                                                "SELECT held_quantity FROM"
                                                        + " inventory_daily_allocations WHERE id=?",
                                                Integer.class,
                                                allocation);
                                if (seen.isEmpty())
                                    executor.submit(
                                                    () ->
                                                            jdbc.update(
                                                                    "UPDATE"
                                                                        + " inventory_daily_allocations"
                                                                        + " SET held_quantity=1"
                                                                        + " WHERE id=?",
                                                                    allocation))
                                            .get(2, java.util.concurrent.TimeUnit.SECONDS);
                                assertThat(
                                                jdbc.queryForObject(
                                                        "SELECT held_quantity FROM"
                                                            + " inventory_daily_allocations WHERE"
                                                            + " id=?",
                                                        Integer.class,
                                                        allocation))
                                        .isEqualTo(before);
                                seen.add(before);
                                return new CartAvailabilityService.Availability(
                                        "PICKUP",
                                        date,
                                        date,
                                        java.util.List.of(),
                                        java.time.Instant.now().plusSeconds(60));
                            });
            var items =
                    java.util.List.of(
                            new com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest(
                                    product, 1, null));
            var first = previews.check(branch, date, items);
            var afterCommit = previews.check(branch, date, items);
            assertThat(afterCommit).isNotSameAs(first);
            assertThat(seen).containsExactly(0, 1);
            assertThat(previews.check(branch, date, items)).isSameAs(afterCommit);
            assertThat(seen).hasSize(2);
        }
    }
}
