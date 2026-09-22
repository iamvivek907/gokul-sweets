package com.gokulsweets.restaurant.payment.provider.paytm;

import com.gokulsweets.restaurant.payment.provider.paytm.dto.PaytmInitiateResponse;
import com.gokulsweets.restaurant.payment.provider.paytm.dto.PaytmStatusResponse;
import com.paytm.pg.merchant.PaytmChecksum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaytmClient {

    private final PaytmProperties properties;

    private final ObjectMapper objectMapper;

    // =========================================================
    // INITIATE TRANSACTION
    // =========================================================

    public PaytmInitiateResponse initiateTransaction(
            String orderId,
            BigDecimal amount,
            String customerId
    ) {

        validateCommonConfiguration();

        if (orderId == null
                || orderId.isBlank()) {

            throw new IllegalArgumentException(
                    "Paytm order ID is required."
            );
        }

        if (amount == null
                || amount.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            throw new IllegalArgumentException(
                    "Paytm transaction amount must be greater than zero."
            );
        }

        if (customerId == null
                || customerId.isBlank()) {

            throw new IllegalArgumentException(
                    "Paytm customer ID is required."
            );
        }

        try {

            /*
             * =================================================
             * BODY
             * =================================================
             */

            Map<String, Object> body =
                    new LinkedHashMap<>();

            body.put(
                    "requestType",
                    "Payment"
            );

            body.put(
                    "mid",
                    properties.getMid()
            );

            body.put(
                    "websiteName",
                    properties.getWebsiteName()
            );

            body.put(
                    "orderId",
                    orderId
            );


            /*
             * Transaction amount.
             */
            Map<String, String> txnAmount =
                    new LinkedHashMap<>();

            txnAmount.put(
                    "value",
                    amount
                            .setScale(
                                    2,
                                    RoundingMode.HALF_UP
                            )
                            .toPlainString()
            );

            txnAmount.put(
                    "currency",
                    "INR"
            );

            body.put(
                    "txnAmount",
                    txnAmount
            );


            /*
             * Customer info.
             */
            Map<String, String> userInfo =
                    new LinkedHashMap<>();

            userInfo.put(
                    "custId",
                    customerId
            );

            body.put(
                    "userInfo",
                    userInfo
            );


            /*
             * Callback URL is optional for the current
             * staging CheckoutJS flow.
             *
             * Add it only when actually configured.
             */
            if (properties.getCallbackUrl() != null
                    && !properties.getCallbackUrl()
                    .isBlank()) {

                body.put(
                        "callbackUrl",
                        properties.getCallbackUrl()
                );
            }


            /*
             * =================================================
             * CHECKSUM
             * =================================================
             */

            String bodyJson =
                    objectMapper.writeValueAsString(
                            body
                    );

            String signature =
                    PaytmChecksum.generateSignature(
                            bodyJson,
                            properties.getMerchantKey()
                    );


            Map<String, String> head =
                    new LinkedHashMap<>();

            head.put(
                    "signature",
                    signature
            );


            /*
             * =================================================
             * COMPLETE REQUEST
             * =================================================
             */

            Map<String, Object> request =
                    new LinkedHashMap<>();

            request.put(
                    "body",
                    body
            );

            request.put(
                    "head",
                    head
            );


            String uri =
                    "/theia/api/v1/initiateTransaction"
                            + "?mid="
                            + properties.getMid()
                            + "&orderId="
                            + orderId;


            log.info(
                    "Calling Paytm initiate transaction: orderId={}, amount={}, environmentBaseUrl={}, websiteName={}",
                    orderId,
                    amount,
                    properties.getBaseUrl(),
                    properties.getWebsiteName()
            );


            /*
             * =================================================
             * CALL PAYTM
             * =================================================
             *
             * exchange() is used instead of retrieve()
             * so we can inspect the response body even when
             * Paytm sends an HTTP error status.
             */

            ResponseEntity<String> httpResponse =
                    RestClient.builder()
                            .baseUrl(
                                    properties.getBaseUrl()
                            )
                            .build()
                            .post()
                            .uri(
                                    uri
                            )
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(
                                    request
                            )
                            .exchange(
                                    (
                                            requestHeaders,
                                            response
                                    ) -> {

                                        String responseBody =
                                                response.bodyTo(
                                                        String.class
                                                );

                                        return ResponseEntity
                                                .status(
                                                        response.getStatusCode()
                                                )
                                                .headers(
                                                        response.getHeaders()
                                                )
                                                .body(
                                                        responseBody
                                                );
                                    }
                            );


            String rawResponse =
                    httpResponse.getBody();


            log.info(
                    "Paytm initiate HTTP response: orderId={}, httpStatus={}",
                    orderId,
                    httpResponse.getStatusCode()
            );


            if (rawResponse == null
                    || rawResponse.isBlank()) {

                throw new IllegalStateException(
                        "Empty response received from Paytm."
                );
            }


            /*
             * Never log request signatures or merchant key.
             *
             * Paytm response content itself is useful for
             * diagnosing resultCode/resultMsg.
             */
            log.debug(
                    "Paytm initiate raw response: orderId={}, body={}",
                    orderId,
                    rawResponse
            );


            /*
             * =================================================
             * PARSE RESPONSE
             * =================================================
             */

            PaytmInitiateResponse response =
                    objectMapper.readValue(
                            rawResponse,
                            PaytmInitiateResponse.class
                    );


            if (response.body() == null
                    || response.body()
                    .resultInfo() == null) {

                log.error(
                        "Invalid Paytm initiate response structure: orderId={}, httpStatus={}, rawResponse={}",
                        orderId,
                        httpResponse.getStatusCode(),
                        rawResponse
                );

                throw new IllegalStateException(
                        "Invalid response received from Paytm."
                );
            }


            String resultStatus =
                    response.body()
                            .resultInfo()
                            .resultStatus();

            String resultCode =
                    response.body()
                            .resultInfo()
                            .resultCode();

            String resultMsg =
                    response.body()
                            .resultInfo()
                            .resultMsg();


            log.info(
                    "Paytm initiate transaction response: orderId={}, httpStatus={}, resultStatus={}, resultCode={}, resultMsg={}",
                    orderId,
                    httpResponse.getStatusCode(),
                    resultStatus,
                    resultCode,
                    resultMsg
            );


            /*
             * Paytm uses:
             *
             * S = Success
             * F = Failure
             * U = Unknown
             */
            if (!"S".equalsIgnoreCase(
                    resultStatus
            )) {

                throw new IllegalStateException(
                        "Paytm rejected transaction initialization. "
                                + "resultStatus="
                                + resultStatus
                                + ", resultCode="
                                + resultCode
                                + ", resultMsg="
                                + resultMsg
                );
            }


            /*
             * Validate response signature after we have logged
             * Paytm's actual result information.
             */
            verifyResponseSignature(
                    rawResponse
            );


            String txnToken =
                    response.body()
                            .txnToken();


            if (txnToken == null
                    || txnToken.isBlank()) {

                throw new IllegalStateException(
                        "Paytm initiation succeeded but no transaction token was returned."
                );
            }


            log.info(
                    "Paytm transaction token created successfully: orderId={}",
                    orderId
            );


            return response;

        } catch (Exception ex) {

            log.error(
                    "Paytm initiate transaction failed: orderId={}, errorType={}, message={}",
                    orderId,
                    ex.getClass()
                            .getSimpleName(),
                    ex.getMessage(),
                    ex
            );


            throw new IllegalStateException(
                    "Unable to initiate Paytm payment.",
                    ex
            );
        }
    }

    // =========================================================
    // TRANSACTION STATUS
    // =========================================================

    public PaytmStatusResponse getTransactionStatus(
            String orderId
    ) {

        validateCommonConfiguration();


        if (orderId == null
                || orderId.isBlank()) {

            throw new IllegalArgumentException(
                    "Paytm order ID is required."
            );
        }


        try {

            /*
             * =================================================
             * BODY
             * =================================================
             */

            Map<String, String> body =
                    new LinkedHashMap<>();

            body.put(
                    "mid",
                    properties.getMid()
            );

            body.put(
                    "orderId",
                    orderId
            );


            /*
             * =================================================
             * CHECKSUM
             * =================================================
             */

            String bodyJson =
                    objectMapper.writeValueAsString(
                            body
                    );

            String signature =
                    PaytmChecksum.generateSignature(
                            bodyJson,
                            properties.getMerchantKey()
                    );


            Map<String, String> head =
                    new LinkedHashMap<>();

            head.put(
                    "signature",
                    signature
            );


            Map<String, Object> request =
                    new LinkedHashMap<>();

            request.put(
                    "body",
                    body
            );

            request.put(
                    "head",
                    head
            );


            log.info(
                    "Checking Paytm transaction status: orderId={}",
                    orderId
            );


            /*
             * =================================================
             * CALL PAYTM STATUS API
             * =================================================
             */

            ResponseEntity<String> httpResponse =
                    RestClient.builder()
                            .baseUrl(
                                    properties.getBaseUrl()
                            )
                            .build()
                            .post()
                            .uri(
                                    "/v3/order/status"
                            )
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(
                                    request
                            )
                            .exchange(
                                    (
                                            requestHeaders,
                                            response
                                    ) -> {

                                        String responseBody =
                                                response.bodyTo(
                                                        String.class
                                                );

                                        return ResponseEntity
                                                .status(
                                                        response.getStatusCode()
                                                )
                                                .headers(
                                                        response.getHeaders()
                                                )
                                                .body(
                                                        responseBody
                                                );
                                    }
                            );


            String rawResponse =
                    httpResponse.getBody();


            log.info(
                    "Paytm status HTTP response: orderId={}, httpStatus={}",
                    orderId,
                    httpResponse.getStatusCode()
            );


            if (rawResponse == null
                    || rawResponse.isBlank()) {

                throw new IllegalStateException(
                        "Empty Paytm status response."
                );
            }


            log.debug(
                    "Paytm transaction status raw response: orderId={}, body={}",
                    orderId,
                    rawResponse
            );


            /*
             * =================================================
             * PARSE RESPONSE
             * =================================================
             */

            PaytmStatusResponse response =
                    objectMapper.readValue(
                            rawResponse,
                            PaytmStatusResponse.class
                    );


            if (response.body() == null
                    || response.body()
                    .resultInfo() == null) {

                log.error(
                        "Invalid Paytm transaction status response structure: orderId={}, httpStatus={}, rawResponse={}",
                        orderId,
                        httpResponse.getStatusCode(),
                        rawResponse
                );

                throw new IllegalStateException(
                        "Invalid Paytm status response."
                );
            }


            String resultStatus =
                    response.body()
                            .resultInfo()
                            .resultStatus();

            String resultCode =
                    response.body()
                            .resultInfo()
                            .resultCode();

            String resultMsg =
                    response.body()
                            .resultInfo()
                            .resultMsg();


            log.info(
                    "Paytm transaction status response: orderId={}, httpStatus={}, resultStatus={}, resultCode={}, resultMsg={}",
                    orderId,
                    httpResponse.getStatusCode(),
                    resultStatus,
                    resultCode,
                    resultMsg
            );


            /*
             * Unlike initiateTransaction(), statuses such as
             * TXN_FAILURE / PENDING / NO_RECORD_FOUND are valid
             * API responses.
             *
             * PaytmPaymentProvider decides how those map to
             * our PaymentStatus enum.
             */
            verifyResponseSignature(
                    rawResponse
            );


            return response;

        } catch (Exception ex) {

            log.error(
                    "Unable to query Paytm transaction status: orderId={}, errorType={}, message={}",
                    orderId,
                    ex.getClass()
                            .getSimpleName(),
                    ex.getMessage(),
                    ex
            );


            throw new IllegalStateException(
                    "Unable to verify payment with Paytm.",
                    ex
            );
        }
    }


    // =========================================================
    // INITIATE REFUND
    // =========================================================

    public PaytmRefundGatewayResult initiateRefund(
            String orderId,
            String providerPaymentId,
            String refundReferenceId,
            BigDecimal refundAmount
    ) {

        validateCommonConfiguration();

        validateRefundInput(
                orderId,
                providerPaymentId,
                refundReferenceId,
                refundAmount
        );

        try {

            Map<String, Object> body =
                    new LinkedHashMap<>();

            body.put(
                    "mid",
                    properties.getMid()
            );

            body.put(
                    "txnType",
                    "REFUND"
            );

            body.put(
                    "orderId",
                    orderId
            );

            body.put(
                    "txnId",
                    providerPaymentId
            );

            body.put(
                    "refId",
                    refundReferenceId
            );

            body.put(
                    "refundAmount",
                    refundAmount
                            .setScale(
                                    2,
                                    RoundingMode.HALF_UP
                            )
                            .toPlainString()
            );

            String bodyJson =
                    objectMapper.writeValueAsString(
                            body
                    );

            String signature =
                    PaytmChecksum.generateSignature(
                            bodyJson,
                            properties.getMerchantKey()
                    );

            Map<String, String> head =
                    new LinkedHashMap<>();

            head.put(
                    "signature",
                    signature
            );

            Map<String, Object> request =
                    new LinkedHashMap<>();

            request.put(
                    "body",
                    body
            );

            request.put(
                    "head",
                    head
            );

            log.info(
                    "Calling Paytm refund API: orderId={}, refundReferenceId={}, amount={}",
                    orderId,
                    refundReferenceId,
                    refundAmount
            );

            String rawResponse =
                    postRefundRequest(
                            "/refund/apply",
                            request
                    );

            verifyRequiredResponseSignature(
                    rawResponse
            );

            return parseRefundGatewayResult(
                    rawResponse,
                    orderId,
                    refundReferenceId
            );

        } catch (Exception ex) {

            log.error(
                    "Paytm refund initiation failed: orderId={}, refundReferenceId={}, errorType={}, message={}",
                    orderId,
                    refundReferenceId,
                    ex.getClass()
                            .getSimpleName(),
                    ex.getMessage(),
                    ex
            );

            throw new IllegalStateException(
                    "Unable to initiate Paytm refund.",
                    ex
            );
        }
    }

    // =========================================================
    // REFUND STATUS
    // =========================================================

    public PaytmRefundGatewayResult getRefundStatus(
            String orderId,
            String refundReferenceId
    ) {

        validateCommonConfiguration();

        if (
                orderId == null
                        ||
                        orderId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Paytm order ID is required."
            );
        }

        if (
                refundReferenceId == null
                        ||
                        refundReferenceId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Refund reference ID is required."
            );
        }

        try {

            Map<String, Object> body =
                    new LinkedHashMap<>();

            body.put(
                    "mid",
                    properties.getMid()
            );

            body.put(
                    "orderId",
                    orderId
            );

            body.put(
                    "refId",
                    refundReferenceId
            );

            String bodyJson =
                    objectMapper.writeValueAsString(
                            body
                    );

            String signature =
                    PaytmChecksum.generateSignature(
                            bodyJson,
                            properties.getMerchantKey()
                    );

            Map<String, String> head =
                    new LinkedHashMap<>();

            head.put(
                    "signature",
                    signature
            );

            Map<String, Object> request =
                    new LinkedHashMap<>();

            request.put(
                    "body",
                    body
            );

            request.put(
                    "head",
                    head
            );

            log.info(
                    "Checking Paytm refund status: orderId={}, refundReferenceId={}",
                    orderId,
                    refundReferenceId
            );

            String rawResponse =
                    postRefundRequest(
                            "/v2/refund/status",
                            request
                    );

            verifyRequiredResponseSignature(
                    rawResponse
            );

            return parseRefundGatewayResult(
                    rawResponse,
                    orderId,
                    refundReferenceId
            );

        } catch (Exception ex) {

            log.error(
                    "Unable to query Paytm refund status: orderId={}, refundReferenceId={}, errorType={}, message={}",
                    orderId,
                    refundReferenceId,
                    ex.getClass()
                            .getSimpleName(),
                    ex.getMessage(),
                    ex
            );

            throw new IllegalStateException(
                    "Unable to verify Paytm refund.",
                    ex
            );
        }
    }

    // =========================================================
    // REFUND HTTP
    // =========================================================

    private String postRefundRequest(
            String path,
            Map<String, Object> request
    ) {

        ResponseEntity<String> httpResponse =
                RestClient.builder()
                        .baseUrl(
                                resolveRefundBaseUrl()
                        )
                        .build()
                        .post()
                        .uri(
                                path
                        )
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .body(
                                request
                        )
                        .exchange(
                                (
                                        requestHeaders,
                                        response
                                ) -> {

                                    String responseBody =
                                            response.bodyTo(
                                                    String.class
                                            );

                                    return ResponseEntity
                                            .status(
                                                    response.getStatusCode()
                                            )
                                            .headers(
                                                    response.getHeaders()
                                            )
                                            .body(
                                                    responseBody
                                            );
                                }
                        );

        String rawResponse =
                httpResponse.getBody();

        log.info(
                "Paytm refund HTTP response: path={}, httpStatus={}",
                path,
                httpResponse.getStatusCode()
        );

        if (
                rawResponse == null
                        ||
                        rawResponse.isBlank()
        ) {

            throw new IllegalStateException(
                    "Empty response received from Paytm refund API."
            );
        }

        /*
         * Refund responses can contain operational identifiers.
         * Keep the full body at DEBUG only.
         */
        log.debug(
                "Paytm refund raw response: path={}, body={}",
                path,
                rawResponse
        );

        return rawResponse;
    }

    // =========================================================
    // REFUND RESPONSE
    // =========================================================

    private PaytmRefundGatewayResult parseRefundGatewayResult(
            String rawResponse,
            String orderId,
            String refundReferenceId
    ) throws Exception {

        JsonNode root =
                objectMapper.readTree(
                        rawResponse
                );

        JsonNode body =
                root.get(
                        "body"
                );

        if (
                body == null
        ) {

            throw new IllegalStateException(
                    "Paytm refund response body is missing."
            );
        }

        JsonNode resultInfo =
                body.get(
                        "resultInfo"
                );

        if (
                resultInfo == null
        ) {

            throw new IllegalStateException(
                    "Paytm refund result information is missing."
            );
        }

        String resultStatus =
                textOrNull(
                        resultInfo.get(
                                "resultStatus"
                        )
                );

        String resultCode =
                textOrNull(
                        resultInfo.get(
                                "resultCode"
                        )
                );

        String resultMessage =
                textOrNull(
                        resultInfo.get(
                                "resultMsg"
                        )
                );

        String providerRefundId =
                textOrNull(
                        body.get(
                                "refundId"
                        )
                );

        log.info(
                "Paytm refund response: orderId={}, refundReferenceId={}, resultStatus={}, resultCode={}, resultMsg={}",
                orderId,
                refundReferenceId,
                resultStatus,
                resultCode,
                resultMessage
        );

        return new PaytmRefundGatewayResult(
                resultStatus,
                resultCode,
                resultMessage,
                providerRefundId
        );
    }

    // =========================================================
    // REQUIRED REFUND SIGNATURE
    // =========================================================

    private void verifyRequiredResponseSignature(
            String rawResponse
    ) throws Exception {

        JsonNode root =
                objectMapper.readTree(
                        rawResponse
                );

        JsonNode bodyNode =
                root.get(
                        "body"
                );

        JsonNode signatureNode =
                root.path(
                                "head"
                        )
                        .get(
                                "signature"
                        );

        if (
                bodyNode == null
        ) {

            throw new IllegalStateException(
                    "Paytm refund response body is missing."
            );
        }

        if (
                signatureNode == null
                        ||
                        signatureNode
                                .asText()
                                .isBlank()
        ) {

            throw new IllegalStateException(
                    "Paytm refund response signature is missing."
            );
        }

        boolean valid =
                PaytmChecksum.verifySignature(
                        bodyNode.toString(),
                        properties.getMerchantKey(),
                        signatureNode.asText()
                );

        if (
                !valid
        ) {

            throw new IllegalStateException(
                    "Invalid response signature received from Paytm refund API."
            );
        }
    }

    // =========================================================
    // REFUND INPUT
    // =========================================================

    private void validateRefundInput(
            String orderId,
            String providerPaymentId,
            String refundReferenceId,
            BigDecimal refundAmount
    ) {

        if (
                orderId == null
                        ||
                        orderId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Paytm order ID is required."
            );
        }

        if (
                providerPaymentId == null
                        ||
                        providerPaymentId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Paytm transaction ID is required for refund."
            );
        }

        if (
                refundReferenceId == null
                        ||
                        refundReferenceId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Refund reference ID is required."
            );
        }

        if (
                refundReferenceId.length() > 50
        ) {

            throw new IllegalArgumentException(
                    "Refund reference ID must not exceed 50 characters."
            );
        }

        if (
                refundAmount == null
                        ||
                        refundAmount.compareTo(
                                BigDecimal.ZERO
                        ) <= 0
        ) {

            throw new IllegalArgumentException(
                    "Refund amount must be greater than zero."
            );
        }
    }

    // =========================================================
    // REFUND BASE URL
    // =========================================================

    private String resolveRefundBaseUrl() {

        String paymentBaseUrl =
                properties.getBaseUrl();

        if (
                paymentBaseUrl == null
                        ||
                        paymentBaseUrl.isBlank()
        ) {

            throw new IllegalStateException(
                    "Paytm base URL is not configured."
            );
        }

        /*
         * Current Paytm refund APIs are hosted on the
         * paytmpayments.com gateway rather than the legacy
         * securegw*.paytm.in payment hostname.
         *
         * Reuse the configured environment:
         *
         * staging    -> securestage.paytmpayments.com
         * production -> secure.paytmpayments.com
         */
        if (
                paymentBaseUrl
                        .toLowerCase()
                        .contains(
                                "stage"
                        )
        ) {

            return "https://securestage.paytmpayments.com";
        }

        return "https://secure.paytmpayments.com";
    }

    private String textOrNull(
            JsonNode node
    ) {

        if (
                node == null
                        ||
                        node.isNull()
        ) {

            return null;
        }

        String value =
                node.asText();

        return value == null
                ||
                value.isBlank()
                ? null
                : value;
    }

    // =========================================================
    // RESPONSE SIGNATURE
    // =========================================================

    private void verifyResponseSignature(
            String rawResponse
    ) throws Exception {

        JsonNode root =
                objectMapper.readTree(
                        rawResponse
                );


        JsonNode bodyNode =
                root.get(
                        "body"
                );


        JsonNode signatureNode =
                root.path(
                                "head"
                        )
                        .get(
                                "signature"
                        );


        if (bodyNode == null) {

            log.error(
                    "Paytm response body is missing."
            );

            throw new IllegalStateException(
                    "Paytm response body is missing."
            );
        }


        /*
         * Some rejected Paytm requests may not include
         * a response signature.
         *
         * By the time this method is called for initiation,
         * successful resultStatus has already been verified.
         */
        if (signatureNode == null
                || signatureNode
                .asText()
                .isBlank()) {

            log.warn(
                    "Paytm response did not contain a response signature."
            );

            return;
        }


        boolean valid =
                PaytmChecksum.verifySignature(
                        bodyNode.toString(),
                        properties.getMerchantKey(),
                        signatureNode.asText()
                );


        if (!valid) {

            log.error(
                    "Paytm response signature validation failed."
            );

            throw new IllegalStateException(
                    "Invalid response signature received from Paytm."
            );
        }


        log.debug(
                "Paytm response signature verified successfully."
        );
    }

    // =========================================================
    // CONFIG VALIDATION
    // =========================================================

    private void validateCommonConfiguration() {

        if (properties.getBaseUrl() == null
                || properties.getBaseUrl()
                .isBlank()) {

            throw new IllegalStateException(
                    "Paytm base URL is not configured."
            );
        }


        if (properties.getMid() == null
                || properties.getMid()
                .isBlank()) {

            throw new IllegalStateException(
                    "Paytm MID is not configured."
            );
        }


        if (properties.getMerchantKey() == null
                || properties.getMerchantKey()
                .isBlank()) {

            throw new IllegalStateException(
                    "Paytm merchant key is not configured."
            );
        }


        if (properties.getWebsiteName() == null
                || properties.getWebsiteName()
                .isBlank()) {

            throw new IllegalStateException(
                    "Paytm website name is not configured."
            );
        }
    }
}
