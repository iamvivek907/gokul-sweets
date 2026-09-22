package com.gokulsweets.restaurant.payment.service;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayWebhookService {

    private final RazorpayClient razorpayClient;
    private final ObjectMapper objectMapper;
    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository eventRepository;
    private final PaymentStatusService paymentStatusService;

    @Transactional
    public void process(
            byte[] rawBody,
            String signature,
            String providerEventId
    ) {
        razorpayClient.verifyWebhookSignature(rawBody, signature);
        validateEventId(providerEventId);

        JsonNode root = parse(rawBody);
        String eventType = requiredText(root, "event");

        int claimed = eventRepository.claim(
                PaymentProviderType.RAZORPAY.name(),
                providerEventId,
                eventType,
                sha256(rawBody)
        );

        if (claimed == 0) {
            log.debug(
                    "Duplicate Razorpay webhook ignored: eventId={}, type={}",
                    providerEventId,
                    eventType
            );
            return;
        }

        switch (eventType) {
            case "payment.captured" -> processCapturedPayment(root);
            case "payment.failed" -> processFailedPayment(root);
            case "refund.processed" -> processRefund(root, true);
            case "refund.failed" -> processRefund(root, false);
            default -> log.debug(
                    "Razorpay webhook type stored without state change: eventId={}, type={}",
                    providerEventId,
                    eventType
            );
        }
    }

    private void processCapturedPayment(JsonNode root) {
        JsonNode providerPayment = paymentEntity(root);
        Payment payment = findLocalPayment(providerPayment);
        validateAmount(payment, providerPayment.path("amount").asLong(-1));

        paymentStatusService.markPaid(
                payment.getId(),
                requiredText(providerPayment, "id")
        );
    }

    private void processFailedPayment(JsonNode root) {
        JsonNode providerPayment = paymentEntity(root);
        Payment payment = findLocalPayment(providerPayment);
        String reason = providerPayment.path("error_description")
                .asText("Razorpay reported that the payment failed.");

        paymentStatusService.markFailed(payment.getId(), reason);
    }

    private void processRefund(JsonNode root, boolean succeeded) {
        JsonNode refund = root.path("payload")
                .path("refund")
                .path("entity");

        String providerPaymentId = requiredText(refund, "payment_id");
        String providerRefundId = requiredText(refund, "id");

        Payment payment = paymentRepository
                .findByProviderAndProviderPaymentId(
                        PaymentProviderType.RAZORPAY,
                        providerPaymentId
                )
                .orElse(null);

        if (payment == null) {
            log.warn(
                    "Razorpay refund webhook has no local payment: providerPaymentId={}, providerRefundId={}",
                    providerPaymentId,
                    providerRefundId
            );
            return;
        }

        if (succeeded) {
            paymentStatusService.markRefunded(
                    payment.getId(),
                    providerRefundId
            );
        } else {
            paymentStatusService.markRefundFailed(
                    payment.getId(),
                    providerRefundId,
                    "Razorpay reported that the refund failed."
            );
        }
    }

    private Payment findLocalPayment(JsonNode providerPayment) {
        String providerOrderId = requiredText(providerPayment, "order_id");
        Payment payment = paymentRepository
                .findByProviderAndProviderOrderId(
                        PaymentProviderType.RAZORPAY,
                        providerOrderId
                )
                .orElseThrow(() -> new IllegalArgumentException(
                        "Webhook payment does not match a local payment."
                ));

        if (payment.getProvider() != PaymentProviderType.RAZORPAY) {
            throw new IllegalArgumentException(
                    "Webhook payment provider does not match."
            );
        }
        return payment;
    }

    private JsonNode paymentEntity(JsonNode root) {
        JsonNode entity = root.path("payload")
                .path("payment")
                .path("entity");
        if (!entity.isObject()) {
            throw new IllegalArgumentException(
                    "Razorpay webhook payment payload is missing."
            );
        }
        return entity;
    }

    private void validateAmount(Payment payment, long providerAmount) {
        long expected = payment.getAmount()
                .movePointRight(2)
                .longValueExact();
        if (providerAmount != expected) {
            throw new IllegalStateException(
                    "Webhook payment amount does not match the local payment."
            );
        }
    }

    private JsonNode parse(byte[] rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Razorpay webhook payload is invalid.",
                    exception
            );
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "Razorpay webhook field is missing: " + field
            );
        }
        return value;
    }

    private void validateEventId(String eventId) {
        if (eventId == null || eventId.isBlank() || eventId.length() > 100) {
            throw new IllegalArgumentException(
                    "A valid Razorpay webhook event ID is required."
            );
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception
            );
        }
    }
}
