package com.gokulsweets.restaurant.staff.notification;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Explicit operator recipients, HTTPS Resend API and stable idempotency keys; no logged
 * credentials.
 */
@Component
@RequiredArgsConstructor
public class StaffAlertEmail {

    private final StaffAlertProperties properties;

    private final Environment environment;

    private final HttpClient client =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    /**
     * Addresses the operation.
     *
     * @param value the value
     * @return the address result
     */
    static boolean address(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAlertEmail.class, "address(String)");
        try {
            return value != null
                    && value.length() <= 254
                    && value.matches(
                            "[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffAlertEmail.class, "address(String)");
        }
    }

    /**
     * Configureds the operation.
     *
     * @return the configured result
     */
    public boolean configured() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAlertEmail.class, "configured()");
        try {
            return properties.isEmailEnabled()
                    && properties.getEmailApiKey().matches("re_[A-Za-z0-9_-]{10,200}")
                    && address(properties.getEmailFrom())
                    && (properties.getEmailReplyTo().isBlank()
                            || address(properties.getEmailReplyTo()))
                    && properties.getEmailSubjectPrefix().length() <= 80
                    && !properties.getEmailSubjectPrefix().contains("\r")
                    && !properties.getEmailSubjectPrefix().contains("\n");
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, StaffAlertEmail.class, "configured()");
        }
    }

    /**
     * Recipients the operation.
     *
     * @param staffId the staff id
     * @return the recipient result
     */
    public String recipient(long staffId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAlertEmail.class, "recipient(long)");
        try {
            if (!configured()) return null;
            try {
                var node =
                        JsonMapper.builder()
                                .build()
                                .readTree(properties.getEmailRecipients())
                                .get(String.valueOf(staffId));
                if (node != null) return address(node.asText()) ? node.asText() : null;
                // Owner-approved QA routing applies only in DEV; PROD needs explicit staff-ID
                // mappings.
                String test = properties.getEmailTestRecipient();
                return "DEV"
                                        .equals(
                                                environment.getProperty(
                                                        "gokul.environment-isolation.environment"))
                                && address(test)
                        ? test
                        : null;
            } catch (Exception invalid) {
                return null;
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffAlertEmail.class, "recipient(long)");
        }
    }

    /**
     * Tests routing.
     *
     * @param staffId the staff id
     * @return the test routing result
     */
    public boolean testRouting(long staffId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAlertEmail.class, "testRouting(long)");
        try {
            return "DEV".equals(environment.getProperty("gokul.environment-isolation.environment"))
                    && address(properties.getEmailTestRecipient())
                    && properties.getEmailTestRecipient().equals(recipient(staffId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffAlertEmail.class, "testRouting(long)");
        }
    }

    /**
     * Prepares the operation.
     *
     * @param staffId the staff id
     * @param title the title
     * @param message the message
     * @param orderNumber the order number
     * @param eventId the event id
     * @return the prepare result
     */
    HttpRequest prepare(
            long staffId, String title, String message, String orderNumber, long eventId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffAlertEmail.class, "prepare(long,String,String,String,long)");
        try {
            String recipient = recipient(staffId);
            if (recipient == null) throw new IllegalStateException("Staff email unavailable");
            String scope = environment.getProperty("gokul.environment-isolation.environment", "");
            String origin =
                    switch (scope) {
                        case "DEV" -> "https://dev.gokulsweets.in";
                        case "PROD" -> "https://gokulsweets.in";
                        default ->
                                throw new IllegalStateException("Unknown staff alert environment");
                    };
            String encoded =
                    java.net.URLEncoder.encode(orderNumber, java.nio.charset.StandardCharsets.UTF_8)
                            .replace("+", "%20");
            String text =
                    message
                            + "\n\nSign in to take action: "
                            + origin
                            + "/admin/orders/"
                            + encoded
                            + "\n\nAlert reference: "
                            + eventId
                            + ". Reading this message does not start preparation or mark an order"
                            + " ready.";
            var payload = new java.util.LinkedHashMap<String, Object>();
            payload.put("from", properties.getEmailFrom());
            payload.put("to", List.of(recipient));
            payload.put("subject", properties.getEmailSubjectPrefix() + " " + title);
            payload.put("text", text);
            if (!properties.getEmailReplyTo().isBlank())
                payload.put("reply_to", properties.getEmailReplyTo());
            String body = JsonMapper.builder().build().writeValueAsString(payload);
            return HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + properties.getEmailApiKey())
                    .header("Content-Type", "application/json")
                    .header(
                            "Idempotency-Key",
                            "gokul-staff-" + scope + "-" + eventId + "-" + staffId)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAlertEmail.class,
                    "prepare(long,String,String,String,long)");
        }
    }

    /**
     * Sends the operation.
     *
     * @param staffId the staff id
     * @param title the title
     * @param message the message
     * @param orderNumber the order number
     * @param eventId the event id
     * @return the send result
     * @throws Exception if the operation cannot complete
     */
    public int send(long staffId, String title, String message, String orderNumber, long eventId)
            throws Exception {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAlertEmail.class, "send(long,String,String,String,long)");
        try {
            return client.send(
                            prepare(staffId, title, message, orderNumber, eventId),
                            HttpResponse.BodyHandlers.discarding())
                    .statusCode();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAlertEmail.class,
                    "send(long,String,String,String,long)");
        }
    }
}
