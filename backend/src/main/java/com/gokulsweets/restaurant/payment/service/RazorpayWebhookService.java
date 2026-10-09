package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.payment.provider.razorpay.RazorpayClient;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.payment.repository.PaymentWebhookEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Coordinates razorpay webhook operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayWebhookService {

    private final RazorpayClient razorpayClient;

    private final ObjectMapper objectMapper;

    private final PaymentRepository paymentRepository;

    private final PaymentWebhookEventRepository eventRepository;

    private final PaymentStatusService paymentStatusService;

    /**
     * Processes razorpay webhook data.
     *
     * <p>Delegates to {@code eventRepository.claim(...)}.
     *
     * @param rawBody the raw body supplied to this method
     * @param signature the signature supplied to this method
     * @param providerEventId the provider event id supplied to this method
     */
    @Transactional
    public void process(byte[] rawBody, String signature, String providerEventId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayWebhookService.class, "process(byte[],String,String)");
        try {
            razorpayClient.verifyWebhookSignature(rawBody, signature);
            validateEventId(providerEventId);
            JsonNode root = parse(rawBody);
            String eventType = requiredText(root, "event");
            int claimed =
                    eventRepository.claim(
                            PaymentProviderType.RAZORPAY.name(),
                            providerEventId,
                            eventType,
                            sha256(rawBody));
            if (claimed == 0) {
                log.debug(
                        "Duplicate Razorpay webhook ignored: eventId={}, type={}",
                        providerEventId,
                        eventType);
                return;
            }
            switch (eventType) {
                case "payment.captured" -> processCapturedPayment(root);
                case "payment.failed" -> processFailedPayment(root);
                case "refund.processed" -> processRefund(root, true);
                case "refund.failed" -> processRefund(root, false);
                default ->
                        log.debug(
                                "Razorpay webhook type stored without state change: eventId={},"
                                        + " type={}",
                                providerEventId,
                                eventType);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayWebhookService.class,
                    "process(byte[],String,String)");
        }
    }

    /**
     * Processes captured payment.
     *
     * @param root the root
     */
    private void processCapturedPayment(JsonNode root) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RazorpayWebhookService.class, "processCapturedPayment(JsonNode)");
        try {
            JsonNode providerPayment = paymentEntity(root);
            Payment payment = findLocalPayment(providerPayment);
            validateAmount(payment, providerPayment.path("amount").asLong(-1));
            paymentStatusService.markPaid(payment.getId(), requiredText(providerPayment, "id"));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayWebhookService.class,
                    "processCapturedPayment(JsonNode)");
        }
    }

    /**
     * Processes failed payment.
     *
     * @param root the root
     */
    private void processFailedPayment(JsonNode root) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayWebhookService.class, "processFailedPayment(JsonNode)");
        try {
            JsonNode providerPayment = paymentEntity(root);
            Payment payment = findLocalPayment(providerPayment);
            String reason =
                    providerPayment
                            .path("error_description")
                            .asText("Razorpay reported that the payment failed.");
            paymentStatusService.markFailed(payment.getId(), reason);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayWebhookService.class,
                    "processFailedPayment(JsonNode)");
        }
    }

    /**
     * Processes refund.
     *
     * @param root the root
     * @param succeeded the succeeded
     */
    private void processRefund(JsonNode root, boolean succeeded) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayWebhookService.class, "processRefund(JsonNode,boolean)");
        try {
            JsonNode refund = root.path("payload").path("refund").path("entity");
            String providerPaymentId = requiredText(refund, "payment_id");
            String providerRefundId = requiredText(refund, "id");
            Payment payment =
                    paymentRepository
                            .findByProviderAndProviderPaymentId(
                                    PaymentProviderType.RAZORPAY, providerPaymentId)
                            .orElse(null);
            if (payment == null) {
                log.warn(
                        "Razorpay refund webhook has no local payment: providerPaymentId={},"
                                + " providerRefundId={}",
                        providerPaymentId,
                        providerRefundId);
                return;
            }
            if (succeeded) {
                paymentStatusService.markRefunded(payment.getId(), providerRefundId);
            } else {
                paymentStatusService.markRefundFailed(
                        payment.getId(),
                        providerRefundId,
                        "Razorpay reported that the refund failed.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayWebhookService.class,
                    "processRefund(JsonNode,boolean)");
        }
    }

    /**
     * Finds local payment.
     *
     * @param providerPayment the provider payment
     * @return the find local payment result
     */
    private Payment findLocalPayment(JsonNode providerPayment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayWebhookService.class, "findLocalPayment(JsonNode)");
        try {
            String providerOrderId = requiredText(providerPayment, "order_id");
            Payment payment =
                    paymentRepository
                            .findByProviderAndProviderOrderId(
                                    PaymentProviderType.RAZORPAY, providerOrderId)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Webhook payment does not match a local"
                                                            + " payment."));
            if (payment.getProvider() != PaymentProviderType.RAZORPAY) {
                throw new IllegalArgumentException("Webhook payment provider does not match.");
            }
            return payment;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayWebhookService.class,
                    "findLocalPayment(JsonNode)");
        }
    }

    /**
     * Payments entity.
     *
     * @param root the root
     * @return the payment entity result
     */
    private JsonNode paymentEntity(JsonNode root) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayWebhookService.class, "paymentEntity(JsonNode)");
        try {
            JsonNode entity = root.path("payload").path("payment").path("entity");
            if (!entity.isObject()) {
                throw new IllegalArgumentException("Razorpay webhook payment payload is missing.");
            }
            return entity;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayWebhookService.class,
                    "paymentEntity(JsonNode)");
        }
    }

    /**
     * Validates amount.
     *
     * @param payment the payment
     * @param providerAmount the provider amount
     */
    private void validateAmount(Payment payment, long providerAmount) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayWebhookService.class, "validateAmount(Payment,long)");
        try {
            long expected = payment.getAmount().movePointRight(2).longValueExact();
            if (providerAmount != expected) {
                throw new IllegalStateException(
                        "Webhook payment amount does not match the local payment.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayWebhookService.class,
                    "validateAmount(Payment,long)");
        }
    }

    /**
     * Parses razorpay webhook data and returns the {@code JsonNode} result.
     *
     * @param rawBody the raw body supplied to this method
     * @return the value of {@code objectMapper.readTree(rawBody)}
     * @throws IllegalArgumentException when the method rejects the request with {@code Razorpay
     *     webhook payload is invalid.}
     */
    private JsonNode parse(byte[] rawBody) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayWebhookService.class, "parse(byte[])");
        try {
            try {
                return objectMapper.readTree(rawBody);
            } catch (Exception exception) {
                throw new IllegalArgumentException(
                        "Razorpay webhook payload is invalid.", exception);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RazorpayWebhookService.class, "parse(byte[])");
        }
    }

    /**
     * Requireds text.
     *
     * @param node the node
     * @param field the field
     * @return the required text result
     */
    private String requiredText(JsonNode node, String field) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayWebhookService.class, "requiredText(JsonNode,String)");
        try {
            String value = node.path(field).asText("");
            if (value.isBlank()) {
                throw new IllegalArgumentException("Razorpay webhook field is missing: " + field);
            }
            return value;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayWebhookService.class,
                    "requiredText(JsonNode,String)");
        }
    }

    /**
     * Validates event id.
     *
     * @param eventId the event id
     */
    private void validateEventId(String eventId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayWebhookService.class, "validateEventId(String)");
        try {
            if (eventId == null || eventId.isBlank() || eventId.length() > 100) {
                throw new IllegalArgumentException(
                        "A valid Razorpay webhook event ID is required.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayWebhookService.class,
                    "validateEventId(String)");
        }
    }

    /**
     * Returns the SHA-256 digest of the supplied value as hexadecimal text.
     *
     * @param value the value supplied to this method
     * @return the {@code String} result
     * @throws IllegalStateException when the method rejects the request with {@code SHA-256 is
     *     unavailable.}
     */
    private String sha256(byte[] value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayWebhookService.class, "sha256(byte[])");
        try {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable.", exception);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RazorpayWebhookService.class, "sha256(byte[])");
        }
    }
}
