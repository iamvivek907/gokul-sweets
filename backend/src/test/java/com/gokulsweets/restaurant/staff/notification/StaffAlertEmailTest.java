package com.gokulsweets.restaurant.staff.notification;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class StaffAlertEmailTest {
    @Test
    void explicitRecipientsOnlyAndStableEnvironmentScopedIdempotencyWithBoundedHttpsRequest() {
        var properties = new StaffAlertProperties();
        var environment =
                new MockEnvironment()
                        .withProperty("gokul.environment-isolation.environment", "DEV");
        var sender = new StaffAlertEmail(properties, environment);
        assertThat(sender.configured()).isFalse();
        assertThat(sender.recipient(12)).isNull();
        properties.setEmailEnabled(true);
        properties.setEmailApiKey("re_synthetic_test_only_key");
        properties.setEmailFrom("alerts@example.invalid");
        properties.setEmailRecipients(
                "{\"12\":\"staff@example.invalid\",\"13\":\"bad\\nrecipient@example.invalid\"}");
        assertThat(sender.recipient(12)).isEqualTo("staff@example.invalid");
        assertThat(sender.recipient(13)).isNull();
        assertThat(sender.recipient(99)).isNull();
        var first = sender.prepare(12, "Preparation overdue", "Order GS-1 is overdue", "GS-1", 42);
        var replay = sender.prepare(12, "Preparation overdue", "Order GS-1 is overdue", "GS-1", 42);
        assertThat(first.uri().toString()).isEqualTo("https://api.resend.com/emails");
        assertThat(first.timeout()).contains(java.time.Duration.ofSeconds(10));
        assertThat(first.headers().firstValue("Idempotency-Key")).contains("gokul-staff-DEV-42-12");
        assertThat(replay.headers().firstValue("Idempotency-Key"))
                .isEqualTo(first.headers().firstValue("Idempotency-Key"));
        assertThatThrownBy(() -> sender.prepare(99, "Overdue", "Action needed", "GS-1", 42))
                .isInstanceOf(IllegalStateException.class);
        environment.withProperty("gokul.environment-isolation.environment", "PROD");
        assertThat(
                        sender.prepare(12, "Overdue", "Action needed", "GS-1", 42)
                                .headers()
                                .firstValue("Idempotency-Key"))
                .contains("gokul-staff-PROD-42-12");
    }

    @Test
    void qaMailboxIsDevOnlyExplicitMappingWinsAndPayloadIncludesReplyToAndTestingSubject()
            throws Exception {
        var properties = new StaffAlertProperties();
        properties.setEmailEnabled(true);
        properties.setEmailApiKey("re_synthetic_test_only_key");
        properties.setEmailFrom("notifications@example.invalid");
        properties.setEmailReplyTo("reply@example.invalid");
        properties.setEmailTestRecipient("qa@example.invalid");
        properties.setEmailSubjectPrefix("[Gokul Sweets DEV QA]");
        var environment =
                new MockEnvironment()
                        .withProperty("gokul.environment-isolation.environment", "DEV");
        var email = new StaffAlertEmail(properties, environment);
        assertThat(email.recipient(99)).isEqualTo("qa@example.invalid");
        assertThat(email.testRouting(99)).isTrue();
        String payload =
                body(email.prepare(99, "Preparation overdue", "Action needed", "GS-1", 42));
        var json = tools.jackson.databind.json.JsonMapper.builder().build().readTree(payload);
        assertThat(json.get("from").asText()).isEqualTo("notifications@example.invalid");
        assertThat(json.get("reply_to").asText()).isEqualTo("reply@example.invalid");
        assertThat(json.get("to").get(0).asText()).isEqualTo("qa@example.invalid");
        assertThat(json.get("subject").asText())
                .isEqualTo("[Gokul Sweets DEV QA] Preparation overdue");
        properties.setEmailRecipients(
                "{\"12\":\"staff@example.invalid\",\"13\":\"bad\\nrecipient@example.invalid\"}");
        assertThat(email.recipient(12)).isEqualTo("staff@example.invalid");
        assertThat(email.testRouting(12)).isFalse();
        assertThat(email.recipient(13)).isNull();
        environment.withProperty("gokul.environment-isolation.environment", "PROD");
        assertThat(email.recipient(99)).isNull();
        assertThat(email.testRouting(99)).isFalse();
        assertThat(email.recipient(12)).isEqualTo("staff@example.invalid");
        properties.setEmailSubjectPrefix("bad\r\nsubject");
        assertThat(email.configured()).isFalse();
        properties.setEmailSubjectPrefix("[QA]");
        properties.setEmailReplyTo("bad\naddress@example.invalid");
        assertThat(email.configured()).isFalse();
    }

    private static String body(java.net.http.HttpRequest request) throws Exception {
        var result = new java.util.concurrent.CompletableFuture<String>();
        var bytes = new java.io.ByteArrayOutputStream();
        request.bodyPublisher()
                .orElseThrow()
                .subscribe(
                        new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
                            public void onSubscribe(
                                    java.util.concurrent.Flow.Subscription subscription) {
                                subscription.request(Long.MAX_VALUE);
                            }

                            public void onNext(java.nio.ByteBuffer buffer) {
                                byte[] value = new byte[buffer.remaining()];
                                buffer.get(value);
                                bytes.writeBytes(value);
                            }

                            public void onError(Throwable failure) {
                                result.completeExceptionally(failure);
                            }

                            public void onComplete() {
                                result.complete(
                                        bytes.toString(java.nio.charset.StandardCharsets.UTF_8));
                            }
                        });
        return result.get(1, java.util.concurrent.TimeUnit.SECONDS);
    }
}
