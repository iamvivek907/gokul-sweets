package com.gokulsweets.restaurant.loyalty;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class LoyaltyReconcilerTest {
    @Test
    void oneBrokenOrderOrAccountDoesNotStarveTheRestOfTheBatch() {
        var jdbc = mock(JdbcTemplate.class);
        var loyalty = mock(LoyaltyService.class);
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        when(jdbc.queryForList(contains("SELECT o.id"), eq(Long.class)))
                .thenReturn(List.of(1L, 2L));
        when(jdbc.queryForList(startsWith("SELECT DISTINCT")))
                .thenReturn(
                        List.of(
                                Map.of("environment", "DEV", "subject_id", first),
                                Map.of("environment", "DEV", "subject_id", second)));
        doThrow(new IllegalStateException("Malformed order")).when(loyalty).reconcile(1L);
        doThrow(new IllegalStateException("Malformed account"))
                .when(loyalty)
                .expireAccount("DEV", first);
        new LoyaltyReconciler(jdbc, loyalty).reconcile();
        verify(loyalty).reconcile(2L);
        verify(loyalty).expireAccount("DEV", second);
    }
}
