package com.gokulsweets.restaurant.staff.notification;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class StaffAlertEmailTest {
    @Test void explicitRecipientsOnlyAndStableEnvironmentScopedIdempotencyWithBoundedHttpsRequest() {
        var properties = new StaffAlertProperties();
        var environment = new MockEnvironment().withProperty("gokul.environment-isolation.environment", "DEV");
        var sender = new StaffAlertEmail(properties, environment);
        assertThat(sender.configured()).isFalse(); assertThat(sender.recipient(12)).isNull();
        properties.setEmailEnabled(true); properties.setEmailApiKey("re_synthetic_test_only_key");
        properties.setEmailFrom("alerts@example.invalid");
        properties.setEmailRecipients("{\"12\":\"staff@example.invalid\",\"13\":\"bad\\nrecipient@example.invalid\"}");
        assertThat(sender.recipient(12)).isEqualTo("staff@example.invalid");
        assertThat(sender.recipient(13)).isNull(); assertThat(sender.recipient(99)).isNull();
        var first = sender.prepare(12, "Preparation overdue", "Order GS-1 is overdue", "GS-1", 42);
        var replay = sender.prepare(12, "Preparation overdue", "Order GS-1 is overdue", "GS-1", 42);
        assertThat(first.uri().toString()).isEqualTo("https://api.resend.com/emails");
        assertThat(first.timeout()).contains(java.time.Duration.ofSeconds(10));
        assertThat(first.headers().firstValue("Idempotency-Key")).contains("gokul-staff-DEV-42-12");
        assertThat(replay.headers().firstValue("Idempotency-Key")).isEqualTo(first.headers().firstValue("Idempotency-Key"));
        assertThatThrownBy(() -> sender.prepare(99, "Overdue", "Action needed", "GS-1", 42)).isInstanceOf(IllegalStateException.class);
        environment.withProperty("gokul.environment-isolation.environment", "PROD");
        assertThat(sender.prepare(12, "Overdue", "Action needed", "GS-1", 42).headers().firstValue("Idempotency-Key")).contains("gokul-staff-PROD-42-12");
    }
}
