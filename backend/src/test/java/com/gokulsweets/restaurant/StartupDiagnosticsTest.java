package com.gokulsweets.restaurant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

import static org.assertj.core.api.Assertions.assertThat;

class StartupDiagnosticsTest {
    @Test
    void diagnosticsAreDisabledByDefaultAndNeverChangeTheStartupWhenDisabled() {
        var application = new SpringApplication(RestaurantBackendApplication.class);
        var original = application.getApplicationStartup();
        StartupDiagnostics.configure(application, false);
        assertThat(application.getApplicationStartup()).isSameAs(original);
        StartupDiagnostics.configure(application, true);
        assertThat(application.getApplicationStartup()).isInstanceOf(BufferingApplicationStartup.class);
    }

    @Test
    void reportIsBoundedSortedAndBufferCanBeReleased() {
        var startup = new BufferingApplicationStartup(64);
        for (int i = 0; i < 30; i++) startup.start("test.step." + i).end();
        var report = StartupDiagnostics.slowest(startup.drainBufferedTimeline().getEvents());
        assertThat(report).hasSize(20);
        for (int i = 1; i < report.size(); i++) {
            assertThat(report.get(i - 1).getDuration()).isGreaterThanOrEqualTo(report.get(i).getDuration());
        }
        assertThat(startup.getBufferedTimeline().getEvents()).isEmpty();
    }
}
