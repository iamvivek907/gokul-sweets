package com.gokulsweets.restaurant.tax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;

import javax.sql.DataSource;

@SpringBootTest
class TaxCollectionSettingsIntegrationTest {
    @Autowired TaxCollectionSettings settings;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ownerToggleWaitsForTheWholeApprovalTransaction(boolean enabled) throws Exception {
        boolean original = settings.enabled();
        try (var owner = dataSource.getConnection();
                var statement = owner.createStatement()) {
            jdbc.update("UPDATE tax_collection_settings SET enabled=? WHERE id=1", enabled);
            owner.setAutoCommit(false);
            String toggle =
                    "UPDATE tax_collection_settings SET enabled=" + !enabled + " WHERE id=1";
            new TransactionTemplate(transactions)
                    .executeWithoutResult(
                            status -> {
                                settings.lockForQuoteApproval();
                                assertThat(settings.enabled()).isEqualTo(enabled);
                                // A separate PostgreSQL session tries to toggle after the first
                                // calculation read.
                                // A short lock timeout proves actual database exclusion rather than
                                // timer scheduling.
                                assertThatThrownBy(
                                                () -> {
                                                    statement.execute(
                                                            "SET LOCAL lock_timeout = '100ms'");
                                                    statement.executeUpdate(toggle);
                                                })
                                        .isInstanceOf(SQLException.class)
                                        .satisfies(
                                                error ->
                                                        assertThat(
                                                                        ((SQLException) error)
                                                                                .getSQLState())
                                                                .isEqualTo("55P03"));
                                try {
                                    owner.rollback();
                                } catch (SQLException error) {
                                    throw new IllegalStateException(error);
                                }
                                // The subsequent quote-line read still sees the calculation's
                                // setting.
                                assertThat(settings.enabled()).isEqualTo(enabled);
                            });
            // After approval commits, the same owner update succeeds. Roll back the test-only edit.
            statement.execute("SET LOCAL lock_timeout = '1s'");
            assertThat(statement.executeUpdate(toggle)).isEqualTo(1);
            owner.rollback();
        } finally {
            jdbc.update("UPDATE tax_collection_settings SET enabled=? WHERE id=1", original);
        }
    }
}
