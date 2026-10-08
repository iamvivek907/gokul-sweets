package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.dto.CreateOrderRequest;
import com.gokulsweets.restaurant.order.dto.UpdatePendingOrderRequest;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.order.service.model.OrderCalculationResult;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderData;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Coordinates checkout quote operations. */
@Service
@RequiredArgsConstructor
public class CheckoutQuoteService {

    private final EnhancementProperties features;

    private final OrderValidationService validation;

    private final OrderCalculationService calculation;

    private final OrderRepository orders;

    private final com.gokulsweets.restaurant.loyalty.LoyaltyService loyalty;

    @Value("${checkout.quote-signing-key:}")
    private String signingKey;

    /**
     * Immutable line data contract.
     *
     * @param name the name
     * @param unitPrice the unit price
     * @param taxRate the tax rate
     * @param taxAmount the tax amount
     * @param total the total
     * @param productId the product id
     */
    public record Line(
            String name,
            String unitPrice,
            String taxRate,
            String taxAmount,
            String total,
            long productId) {}

    /**
     * Immutable quote data contract.
     *
     * @param items the items
     * @param subtotal the subtotal
     * @param taxAmount the tax amount
     * @param priorityCharge the priority charge
     * @param convenienceFee the convenience fee
     * @param convenienceFeeTax the convenience fee tax
     * @param totalAmount the total amount
     * @param currency the currency
     * @param expiresAt the expires at
     * @param token the token
     * @param paymentFee the payment fee
     * @param paymentFeeTax the payment fee tax
     * @param paymentFeeRate the payment fee rate
     * @param paymentFeeTaxRate the payment fee tax rate
     */
    public record Quote(
            List<Line> items,
            String subtotal,
            String taxAmount,
            String priorityCharge,
            String convenienceFee,
            String convenienceFeeTax,
            String totalAmount,
            String currency,
            String expiresAt,
            String token,
            String paymentFee,
            String paymentFeeTax,
            String paymentFeeRate,
            String paymentFeeTaxRate) {}

    /**
     * Previews the operation.
     *
     * @param request the request
     * @param pendingOrderNumber the pending order number
     * @return the preview result
     */
    @Transactional(readOnly = true)
    public Quote preview(CreateOrderRequest request, String pendingOrderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CheckoutQuoteService.class, "preview(CreateOrderRequest,String)");
        try {
            if (!features.isAcceptedCheckoutQuote()) {
                throw new IllegalStateException("Checkout quotes are not enabled.");
            }
            ValidatedOrderData data;
            if (pendingOrderNumber == null) {
                data = validation.validate(request);
            } else {
                Order order =
                        orders.findByOrderNumber(pendingOrderNumber)
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        "Order does not exist."));
                if (!order.getBranch().getId().equals(request.branchId())) {
                    throw new IllegalStateException(
                            "The branch changed. Review your checkout again.");
                }
                data =
                        validation.validateExistingReservationUpdate(
                                order,
                                request.pickupSlotId(),
                                request.pickupType(),
                                request.items());
            }
            OrderCalculationResult amounts = calculation.calculate(data);
            long expiry = Instant.now().plusSeconds(300).getEpochSecond();
            String payload = payload(request, pendingOrderNumber, amounts, false);
            String token = expiry + "." + sign(expiry + ":" + payload);
            return new Quote(
                    amounts.items().stream()
                            .map(
                                    item ->
                                            new Line(
                                                    item.product().getName(),
                                                    item.unitPrice().toPlainString(),
                                                    item.taxRate().toPlainString(),
                                                    item.taxAmount().toPlainString(),
                                                    item.lineTotal().toPlainString(),
                                                    item.product().getId()))
                            .toList(),
                    amounts.subtotal().toPlainString(),
                    amounts.taxAmount().toPlainString(),
                    amounts.priorityCharge().toPlainString(),
                    amounts.convenienceFee().toPlainString(),
                    amounts.convenienceFeeTax().toPlainString(),
                    amounts.totalAmount().toPlainString(),
                    "INR",
                    Instant.ofEpochSecond(expiry).toString(),
                    token,
                    amounts.paymentFee().toPlainString(),
                    amounts.paymentFeeTax().toPlainString(),
                    amounts.paymentFeeRate().toPlainString(),
                    amounts.paymentFeeTaxRate().toPlainString());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CheckoutQuoteService.class,
                    "preview(CreateOrderRequest,String)");
        }
    }

    /**
     * Accepts the operation.
     *
     * @param request the request
     * @param orderNumber the order number
     * @param amounts the amounts
     * @param token the token
     */
    public void accept(
            CreateOrderRequest request,
            String orderNumber,
            OrderCalculationResult amounts,
            String token) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CheckoutQuoteService.class,
                        "accept(CreateOrderRequest,String,OrderCalculationResult,String)");
        try {
            if (!features.isAcceptedCheckoutQuote()) return;
            if (token == null || !token.matches("[0-9]{10,12}\\.[A-Za-z0-9_-]{43}")) {
                throw new IllegalStateException("Review the current price before continuing.");
            }
            String[] parts = token.split("\\.", 2);
            long expiry = Long.parseLong(parts[0]);
            if (expiry <= Instant.now().getEpochSecond()) {
                throw new IllegalStateException(
                        "This price quote expired. Review the current price again.");
            }
            String expected = sign(parts[0] + ":" + payload(request, orderNumber, amounts, true));
            if (!MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    parts[1].getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalStateException(
                        "Your price or pickup details changed. Review the updated quote.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CheckoutQuoteService.class,
                    "accept(CreateOrderRequest,String,OrderCalculationResult,String)");
        }
    }

    /**
     * Accepts update.
     *
     * @param order the order
     * @param request the request
     * @param amounts the amounts
     */
    public void acceptUpdate(
            Order order, UpdatePendingOrderRequest request, OrderCalculationResult amounts) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CheckoutQuoteService.class,
                        "acceptUpdate(Order,UpdatePendingOrderRequest,OrderCalculationResult)");
        try {
            if (!features.isAcceptedCheckoutQuote()) return;
            accept(
                    new CreateOrderRequest(
                            order.getBranch().getId(),
                            request.pickupSlotId(),
                            order.getCustomerName(),
                            order.getCustomerPhone(),
                            request.pickupType(),
                            request.items(),
                            request.quoteToken()),
                    order.getOrderNumber(),
                    amounts,
                    request.quoteToken());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CheckoutQuoteService.class,
                    "acceptUpdate(Order,UpdatePendingOrderRequest,OrderCalculationResult)");
        }
    }

    /**
     * Payloads the operation.
     *
     * @param request the request
     * @param orderNumber the order number
     * @param amounts the amounts
     * @param lockPolicy the lock policy
     * @return the payload result
     */
    private String payload(
            CreateOrderRequest request,
            String orderNumber,
            OrderCalculationResult amounts,
            boolean lockPolicy) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CheckoutQuoteService.class,
                        "payload(CreateOrderRequest,String,OrderCalculationResult,boolean)");
        try {
            StringBuilder value =
                    new StringBuilder("v1|")
                            .append(orderNumber == null ? "new" : orderNumber)
                            .append('|')
                            .append(request.branchId())
                            .append('|')
                            .append(request.pickupSlotId())
                            .append('|')
                            .append(request.pickupType())
                            .append('|')
                            .append(request.customerName())
                            .append('|')
                            .append(request.customerPhone())
                            .append('|');
            request.items()
                    .forEach(
                            item ->
                                    value.append(item.productId())
                                            .append(':')
                                            .append(item.quantity())
                                            .append(':')
                                            .append(item.weightGrams())
                                            .append(';'));
            amounts.items()
                    .forEach(
                            item ->
                                    value.append(item.product().getId())
                                            .append(':')
                                            .append(item.unitPrice())
                                            .append(':')
                                            .append(item.taxRate())
                                            .append(':')
                                            .append(item.taxAmount())
                                            .append(':')
                                            .append(item.lineTotal())
                                            .append(';'));
            return value.append("|loyalty-policy:")
                    .append(lockPolicy ? loyalty.lockPolicyVersion() : loyalty.policyVersion())
                    .append("|reward:")
                    .append(request.rewardCode())
                    .append("|offer:")
                    .append(request.offerCode())
                    .append('|')
                    .append(amounts.subtotal())
                    .append('|')
                    .append(amounts.taxAmount())
                    .append('|')
                    .append(amounts.priorityCharge())
                    .append('|')
                    .append(amounts.convenienceFee())
                    .append('|')
                    .append(amounts.convenienceFeeTax())
                    .append('|')
                    .append(amounts.totalAmount())
                    .append('|')
                    .append(amounts.feeConfigurationVersion())
                    .append('|')
                    .append(amounts.convenienceFeeTaxRate())
                    .append('|')
                    .append(amounts.paymentFee())
                    .append('|')
                    .append(amounts.paymentFeeTax())
                    .append('|')
                    .append(amounts.paymentFeeRate())
                    .append('|')
                    .append(amounts.paymentFeeTaxRate())
                    .toString();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CheckoutQuoteService.class,
                    "payload(CreateOrderRequest,String,OrderCalculationResult,boolean)");
        }
    }

    /**
     * Signs the operation.
     *
     * @param message the message
     * @return the sign result
     */
    private String sign(String message) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CheckoutQuoteService.class, "sign(String)");
        try {
            if (signingKey == null || signingKey.length() < 32) {
                throw new IllegalStateException(
                        "Checkout quote signing key must contain at least 32 characters.");
            }
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(
                        new SecretKeySpec(
                                signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                return Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
            } catch (java.security.GeneralSecurityException exception) {
                throw new IllegalStateException("Checkout quote signing failed.", exception);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CheckoutQuoteService.class, "sign(String)");
        }
    }
}
