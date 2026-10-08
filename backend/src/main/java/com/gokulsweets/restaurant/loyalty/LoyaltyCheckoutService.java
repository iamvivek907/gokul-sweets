package com.gokulsweets.restaurant.loyalty;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.rebate.RebateApplicationService;
import com.gokulsweets.restaurant.rebate.dto.AppliedRebateResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates loyalty checkout operations. */
@Service
@RequiredArgsConstructor
public class LoyaltyCheckoutService {

    private final OrderRepository orders;

    private final PaymentRepository payments;

    private final LoyaltyService loyalty;

    private final RebateApplicationService rebates;

    /** Immutable checkout data contract. */
    public record Checkout(
            LoyaltyService.Wallet rewards,
            String rewardCode,
            int coins,
            java.math.BigDecimal rewardDiscount,
            AppliedRebateResponse offer) {}

    /**
     * Reads the operation.
     *
     * @param number the number
     * @return the read result
     */
    @Transactional
    public Checkout read(String number) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyCheckoutService.class, "read(String)");
        try {
            var order = orders.findForUpdate(number).orElseThrow();
            return new Checkout(
                    loyalty.orderWallet(order),
                    order.getLoyaltyRewardCode(),
                    order.getLoyaltyCoins(),
                    order.getLoyaltyDiscount(),
                    null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyCheckoutService.class, "read(String)");
        }
    }

    /**
     * Selects the operation.
     *
     * @param number the number
     * @param code the code
     * @param policyVersion the policy version
     * @return the select result
     */
    @Transactional
    public Checkout select(String number, String code, String policyVersion) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyCheckoutService.class, "select(String,String,String)");
        try {
            var order = orders.findForUpdate(number).orElseThrow();
            if (order.getOrderStatus() != OrderStatus.PENDING_PAYMENT
                    || payments.existsByOrderId(order.getId()))
                throw new IllegalStateException("Rewards cannot change after payment starts.");
            if (!loyalty.enabled())
                throw new IllegalStateException("Rewards are currently unavailable.");
            if (code != null && !code.isBlank()) loyalty.verifyPolicy(policyVersion);
            String manualCode = order.isRebateManualSelection() ? order.getRebateCode() : null;
            loyalty.remove(order);
            rebates.remove(number);
            loyalty.reserve(order, code);
            orders.saveAndFlush(order);
            var offer = rebates.reapplyManualOrBest(number, manualCode);
            return new Checkout(
                    loyalty.orderWallet(order),
                    order.getLoyaltyRewardCode(),
                    order.getLoyaltyCoins(),
                    order.getLoyaltyDiscount(),
                    offer);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    LoyaltyCheckoutService.class,
                    "select(String,String,String)");
        }
    }
}
