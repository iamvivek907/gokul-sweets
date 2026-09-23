package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.payment.provider.phonepe.PhonePeClient;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhonePeCallbackService {

    private static final String CALLBACK_PATH = "/api/payments/webhooks/phonepe";

    private final PhonePeClient phonePeClient;
    private final ObjectMapper objectMapper;
    private final PaymentRepository paymentRepository;
    private final PaymentStatusService paymentStatusService;

    @Transactional
    public void process(
            byte[] rawBody,
            String signature
    ) {
        phonePeClient.verifyCallbackSignature(rawBody, signature, CALLBACK_PATH);

        JsonNode root = parse(rawBody);
        String encodedResponse = requiredText(root, "response");
        JsonNode response = phonePeClient.decodeCallbackResponse(encodedResponse);

        JsonNode data = response.path("data");
        String merchantTransactionId = requiredText(data, "merchantTransactionId");
        String providerTransactionId = textOrNull(data, "transactionId");
        String state = requiredText(data, "state").trim().toUpperCase();

        Payment payment = paymentRepository
                .findByProviderAndProviderOrderId(
                        PaymentProviderType.PHONEPE,
                        merchantTransactionId
                )
                .orElseThrow(() -> new IllegalArgumentException(
                        "PhonePe callback does not match a local payment."
                ));

        switch (state) {
            case "COMPLETED" -> paymentStatusService.markPaid(
                    payment.getId(),
                    providerTransactionId
            );
            case "FAILED" -> paymentStatusService.markFailed(
                    payment.getId(),
                    "PhonePe reported that the payment failed."
            );
            default -> log.debug(
                    "PhonePe callback received non-terminal state: paymentId={}, state={}",
                    payment.getId(),
                    state
            );
        }
    }

    private JsonNode parse(byte[] rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "PhonePe callback payload is invalid.",
                    exception
            );
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = textOrNull(node, field);
        if (value == null) {
            throw new IllegalArgumentException(
                    "PhonePe callback field is missing: " + field
            );
        }
        return value;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank()
                ? value.asText()
                : null;
    }
}
