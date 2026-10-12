package com.gokulsweets.restaurant.customer.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Executes the actual recovery migration against a historical identity chain. */
@SpringBootTest
@Transactional
class VerifiedAccountRecoveryMigrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void recoversVerifiedChainWithoutChangingFinancialRowsOrCrossingEnvironments()
            throws Exception {
        String schema = "recovery_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        jdbc.execute("SET LOCAL search_path TO " + schema);
        jdbc.execute(
                new ClassPathResource("identity-account-recovery-fixture.sql")
                        .getContentAsString(StandardCharsets.UTF_8));
        var before = jdbc.queryForList("SELECT * FROM loyalty_ledger ORDER BY id");
        var lots = jdbc.queryForList("SELECT * FROM loyalty_lots ORDER BY ledger_id");
        var holds = jdbc.queryForList("SELECT * FROM loyalty_holds");
        var activity = jdbc.queryForList("SELECT * FROM loyalty_accounts ORDER BY subject_id");
        jdbc.execute(
                new ClassPathResource("db/migration/V129__stable_verified_customer_accounts.sql")
                        .getContentAsString(StandardCharsets.UTF_8));
        assertThat(jdbc.queryForList("SELECT * FROM loyalty_ledger ORDER BY id")).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM loyalty_lots ORDER BY ledger_id"))
                .isEqualTo(lots);
        assertThat(jdbc.queryForList("SELECT * FROM loyalty_holds")).isEqualTo(holds);
        assertThat(
                        jdbc.queryForList(
                                "SELECT * FROM loyalty_accounts WHERE"
                                    + " subject_id<>'00000000-0000-0000-0000-000000000003' ORDER BY"
                                    + " subject_id"))
                .isEqualTo(activity);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT SUM(coins)::integer FROM loyalty_account_ledger WHERE"
                                        + " environment='DEV' AND"
                                        + " subject_id='00000000-0000-0000-0000-000000000003'",
                                Integer.class))
                .isEqualTo(50);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM verified_customer_account_aliases",
                                Integer.class))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM verified_order_transfer_audit",
                                Integer.class))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM verified_order_ownership WHERE"
                                    + " environment='DEV' AND"
                                    + " verified_subject_id='00000000-0000-0000-0000-000000000003'",
                                Integer.class))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT verified_subject_id FROM verified_order_ownership WHERE"
                                        + " order_id=3",
                                UUID.class))
                .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM loyalty_account_qualifying_orders WHERE"
                                        + " subject_id='00000000-0000-0000-0000-000000000003'",
                                Integer.class))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM loyalty_account_ledger WHERE"
                                        + " environment='PROD'",
                                Integer.class))
                .isZero();
    }
}
