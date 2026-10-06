package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.occasion.OccasionCommitmentService;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.payment.provider.phonepe.PhonePeClient;
import com.gokulsweets.restaurant.payment.provider.phonepe.PhonePePaymentProvider;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhonePeCallbackService {

    private final PhonePeClient phonePeClient;
    private final PhonePePaymentProvider phonePeProvider;
    private final ObjectMapper objectMapper;
    private final PaymentRepository paymentRepository;
    private final PaymentStatusService paymentStatusService;
    private final OccasionCommitmentService occasionCommitments;

    public void process(
            byte[] rawBody,
            String checksumKeyId,
            String checksumSignature
    ) {
        phonePeClient.verifyWebhookSignature(
                rawBody,
                checksumKeyId,
                checksumSignature
        );

        JsonNode root = parse(rawBody);

        String event =
                requiredText(
                        root,
                        "event"
                );

        JsonNode payload =
                root.path("payload");

        if (payload.isMissingNode()
                || payload.isNull()) {

            throw new IllegalArgumentException(
                    "PhonePe webhook payload is missing."
            );
        }

        if ("pg.refund.completed".equals(event) || "pg.refund.failed".equals(event)) {
            processRefund(event, payload);
            return;
        }
        if (!"checkout.order.completed".equals(event) && !"checkout.order.failed".equals(event)) {
            return;
        }

        String merchantOrderId =
                requiredText(
                        payload,
                        "merchantOrderId"
                );

        String state =
                requiredText(
                        payload,
                        "state"
                )
                        .trim()
                        .toUpperCase();

        if (occasionCommitments.ownsMerchantOrder(merchantOrderId)) {
            occasionCommitments.verifiedWebhook(merchantOrderId, event, state,
                    extractLatestTransactionId(payload));
            return;
        }

        Payment payment =
                paymentRepository
                        .findByProviderAndProviderOrderId(
                                PaymentProviderType.PHONEPE,
                                merchantOrderId
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "PhonePe webhook does not match a local payment."
                                )
                        );

        String providerTransactionId =
                extractLatestTransactionId(
                        payload
                );

        switch (event) {

            case "checkout.order.completed" -> {

                /*
                 * PhonePe says payment status should be based
                 * on root payload.state.
                 */
                if (!"COMPLETED".equals(state)) {
                    log.warn(
                            "PhonePe completed event has unexpected state: paymentId={}, state={}",
                            payment.getId(),
                            state
                    );

                    return;
                }

                paymentStatusService.markPaid(
                        payment.getId(),
                        providerTransactionId
                );
            }

            case "checkout.order.failed" -> {

                if (!"FAILED".equals(state)) {
                    log.warn(
                            "PhonePe failed event has unexpected state: paymentId={}, state={}",
                            payment.getId(),
                            state
                    );

                    return;
                }

                paymentStatusService.markFailed(
                        payment.getId(),
                        buildFailureReason(payload)
                );
            }

            default -> log.debug(
                    "Ignoring unsupported PhonePe webhook event: paymentId={}, event={}, state={}",
                    payment.getId(),
                    event,
                    state
            );
        }
    }

    private void processRefund(String event, JsonNode payload) {
        String reference = requiredText(payload, "merchantRefundId");
        Payment payment = paymentRepository.findByRefundReferenceId(reference)
                .filter(p -> p.getProvider() == PaymentProviderType.PHONEPE)
                .orElseThrow(() -> new IllegalArgumentException("PhonePe refund does not match a local payment."));
        var result = phonePeProvider.verifyRefundCallback(payment, phonePeClient.refundResponse(payload));
        PaymentStatus expected = "pg.refund.completed".equals(event)
                ? PaymentStatus.REFUNDED : PaymentStatus.REFUND_FAILED;
        if (result.paymentStatus() != expected) {
            throw new IllegalArgumentException("PhonePe refund event and state do not match.");
        }
        if (expected == PaymentStatus.REFUNDED) {
            paymentStatusService.markRefunded(payment.getId(), result.providerRefundId());
        } else {
            paymentStatusService.markRefundFailed(payment.getId(), result.providerRefundId(), result.failureReason());
        }
    }

    private String extractLatestTransactionId(
            JsonNode payload
    ) {
        JsonNode paymentDetails =
                payload.path("paymentDetails");

        if (!paymentDetails.isArray()
                || paymentDetails.isEmpty()) {
            return null;
        }

        /*
         * PhonePe returns payment attempts here.
         * Use the latest available transaction ID.
         */
        for (int index =
             paymentDetails.size() - 1;
             index >= 0;
             index--) {

            String transactionId =
                    textOrNull(
                            paymentDetails.get(index),
                            "transactionId"
                    );

            if (transactionId != null) {
                return transactionId;
            }
        }

        return null;
    }

    private String buildFailureReason(
            JsonNode payload
    ) {
        JsonNode paymentDetails =
                payload.path("paymentDetails");

        if (paymentDetails.isArray()
                && !paymentDetails.isEmpty()) {

            JsonNode latest =
                    paymentDetails.get(
                            paymentDetails.size() - 1
                    );

            String errorCode =
                    textOrNull(
                            latest,
                            "errorCode"
                    );

            String detailedErrorCode =
                    textOrNull(
                            latest,
                            "detailedErrorCode"
                    );

            if (errorCode != null
                    && detailedErrorCode != null) {

                return "PhonePe payment failed: "
                        + errorCode
                        + " ("
                        + detailedErrorCode
                        + ").";
            }

            if (errorCode != null) {
                return "PhonePe payment failed: "
                        + errorCode
                        + ".";
            }
        }

        return "PhonePe reported that the payment failed.";
    }

    private JsonNode parse(
            byte[] rawBody
    ) {
        try {
            return objectMapper.readTree(
                    rawBody
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "PhonePe webhook payload is invalid.",
                    exception
            );
        }
    }

    private String requiredText(
            JsonNode node,
            String field
    ) {
        String value =
                textOrNull(
                        node,
                        field
                );

        if (value == null) {
            throw new IllegalArgumentException(
                    "PhonePe webhook field is missing: "
                            + field
            );
        }

        return value;
    }

    private String textOrNull(
            JsonNode node,
            String field
    ) {
        JsonNode value =
                node.path(field);

        return value.isTextual()
                && !value.asText().isBlank()
                ? value.asText()
                : null;
    }
}
