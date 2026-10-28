package com.gokulsweets.restaurant.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class StorefrontPerformanceTest {
    @Test
    void boundedAnonymousMetricsKeepLayoutShiftSeparateFromMilliseconds() {
        var performance = new StorefrontPerformance();
        for (int i = 0; i < 20; i++)
            performance.record(new StorefrontVitalsController.Vital("LCP", 1800, "good", "menu"));
        performance.record(new StorefrontVitalsController.Vital("CLS", .08, "good", "home"));
        performance.record(
                new StorefrontVitalsController.Vital("FUNNEL_PAID", 1, "measured", "checkout"));
        performance.record(
                new StorefrontVitalsController.Vital(
                        "IMAGE_LOAD", 50, "measured", "/orders/private"));
        performance.record(new StorefrontVitalsController.Vital("LCP", Double.NaN, "good", "menu"));
        performance.record(new StorefrontVitalsController.Vital(null, 20, "good", "menu"));
        assertThat(performance.snapshot().metrics()).hasSize(2);
        var lcp =
                performance.snapshot().metrics().stream()
                        .filter(row -> row.name().equals("LCP"))
                        .findFirst()
                        .orElseThrow();
        assertThat(lcp.samples()).isEqualTo(20);
        assertThat(lcp.p75UpperBound()).isEqualTo(2500);
        assertThat(lcp.unit()).isEqualTo("ms");
        var cls =
                performance.snapshot().metrics().stream()
                        .filter(row -> row.name().equals("CLS"))
                        .findFirst()
                        .orElseThrow();
        assertThat(cls.unit()).isEqualTo("ratio");
        assertThat(cls.p75UpperBound()).isEqualTo(.1);
    }

    @Test
    void permissionIsRequiredBeforeAggregateReads() {
        var staff = mock(StaffAuthorizationService.class);
        var performance = mock(StorefrontPerformance.class);
        doThrow(new AccessDeniedException("denied"))
                .when(staff)
                .requirePermission(PermissionName.MENU_MANAGE);
        assertThatThrownBy(
                        () ->
                                new AdminStorefrontPerformanceController(performance, staff)
                                        .snapshot())
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(performance);
    }
}
