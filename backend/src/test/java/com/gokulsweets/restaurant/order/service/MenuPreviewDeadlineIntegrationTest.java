package com.gokulsweets.restaurant.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;

import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Exercises actual PostgreSQL cancellation and pooled-connection cleanup. */
@SpringBootTest(
        properties = {
            "spring.datasource.hikari.maximum-pool-size=1",
            "spring.datasource.hikari.minimum-idle=1"
        })
class MenuPreviewDeadlineIntegrationTest {
    @Autowired MenuPreviewReads reads;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @MockitoBean CartAvailabilityService service;

    @Test
    void stalledSqlIsCanceledWithoutLeakingTimeoutAndNextRequestReadsFresh() {
        String originalTimeout = jdbc.queryForObject("SHOW statement_timeout", String.class);
        LocalDate date = LocalDate.of(2026, 10, 10);
        var result = new CartAvailabilityService.Availability("PICKUP", date, date, List.of());
        when(service.check(eq(1L), eq(date), eq(1), anyList(), eq(true)))
                .thenAnswer(
                        call -> {
                            assertThat(jdbc.queryForObject("SHOW statement_timeout", String.class))
                                    .isEqualTo("5s");
                            Integer jdbcPid =
                                    jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                            Integer jpaPid =
                                    entityManager
                                            .unwrap(Session.class)
                                            .doReturningWork(
                                                    connection -> {
                                                        try (var statement =
                                                                        connection
                                                                                .createStatement();
                                                                var rows =
                                                                        statement.executeQuery(
                                                                                "SELECT"
                                                                                    + " pg_backend_pid()")) {
                                                            rows.next();
                                                            return rows.getInt(1);
                                                        }
                                                    });
                            // JDBC's SET LOCAL must govern Hibernate's stock queries on the same
                            // connection.
                            assertThat(jpaPid).isEqualTo(jdbcPid);
                            jdbc.execute("SELECT pg_sleep(15)");
                            return result;
                        })
                .thenReturn(result);
        long started = System.nanoTime();
        assertThatThrownBy(() -> reads.check(1L, date, List.of()))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode().value()).isEqualTo(503));
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(10_000);
        assertThat(jdbc.queryForObject("SHOW statement_timeout", String.class))
                .isEqualTo(originalTimeout);
        assertThat(reads.check(1L, date, List.of())).isSameAs(result);
        verify(service, times(2)).check(1L, date, 1, List.of(), true);
        assertThat(jdbc.queryForObject("SHOW statement_timeout", String.class))
                .isEqualTo(originalTimeout);
    }
}
