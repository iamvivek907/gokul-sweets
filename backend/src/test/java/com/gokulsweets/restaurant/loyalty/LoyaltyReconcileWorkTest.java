package com.gokulsweets.restaurant.loyalty;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class LoyaltyReconcileWorkTest {
    @Test
    void repeatedEmptySchedulerTicksDoNotRepeatDatabaseQueries() {
        var queries = new AtomicInteger();
        var jdbc =
                mock(
                        JdbcTemplate.class,
                        invocation -> {
                            if (invocation.getMethod().getName().equals("query")) {
                                queries.incrementAndGet();
                                return List.of();
                            }
                            return Answers.RETURNS_DEFAULTS.answer(invocation);
                        });
        var metrics = new SimpleMeterRegistry();
        var worker = new LoyaltyReconcileWork(jdbc, metrics);
        assertThat(worker.claim()).isEmpty();
        assertThat(worker.claim()).isEmpty();
        assertThat(worker.claim()).isEmpty();
        assertThat(queries).hasValue(1);
        assertThat(metrics.get("gokul.loyalty.queue.polls").counter().count()).isEqualTo(1);
    }

    @Test
    void queryFailuresDoNotStartAnEmptyQueueBackoff() {
        var queries = new AtomicInteger();
        var jdbc =
                mock(
                        JdbcTemplate.class,
                        invocation -> {
                            if (invocation.getMethod().getName().equals("query")) {
                                queries.incrementAndGet();
                                throw new IllegalStateException("offline");
                            }
                            return Answers.RETURNS_DEFAULTS.answer(invocation);
                        });
        var worker = new LoyaltyReconcileWork(jdbc, new SimpleMeterRegistry());
        assertThatThrownBy(worker::claim).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(worker::claim).isInstanceOf(IllegalStateException.class);
        assertThat(queries).hasValue(2);
    }
}
