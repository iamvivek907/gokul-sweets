package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.rebate.dto.AvailableRebateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RebateEligibilityService {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Kolkata");

    private final OrderRepository orderRepository;

    private final PaymentRepository paymentRepository;

    private final RebateRepository rebateRepository;

    private final RebateSlabRepository rebateSlabRepository;

    private final RebateCustomerRepository rebateCustomerRepository;

    private final RebateRedemptionRepository rebateRedemptionRepository;

    @Transactional(readOnly = true)
    public List<AvailableRebateResponse> getAvailableRebates(
            String orderNumber
    ) {

        Order order =
                orderRepository
                        .findDetailedByOrderNumber(orderNumber)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Order does not exist."
                                )
                        );

        /*
         * Rebates can only be selected while
         * waiting for payment.
         */
        if (order.getOrderStatus()
                != OrderStatus.PENDING_PAYMENT) {

            throw new IllegalStateException(
                    "Rebates are only available before payment."
            );
        }

        /*
         * Once payment processing starts, the amount
         * must no longer change.
         */
        if (paymentRepository.existsByOrderId(
                order.getId()
        )) {

            throw new IllegalStateException(
                    "Rebate cannot be selected after payment processing has started."
            );
        }

        BigDecimal eligibleAmount =
                calculateEligibleAmount(order);

        LocalDateTime now =
                LocalDateTime.now(
                        BUSINESS_ZONE
                );

        List<Rebate> candidates =
                rebateRepository
                        .findActivePublicCandidates(
                                order.getBranch().getId(),
                                now
                        );

        List<AvailableRebateResponse> available =
                new ArrayList<>();

        for (Rebate rebate : candidates) {

            if (!isScopeEligible(
                    rebate,
                    order
            )) {
                continue;
            }

            if (!isUsageEligible(
                    rebate,
                    order
            )) {
                continue;
            }

            AvailableRebateResponse response =
                    calculateAvailableRebate(
                            rebate,
                            eligibleAmount
                    );

            /*
             * Null means minimum spend/slab threshold
             * has not been reached.
             */
            if (response != null) {
                available.add(response);
            }
        }

        /*
         * Show the highest-value rebate first.
         */
        available.sort(
                Comparator.comparing(
                        AvailableRebateResponse::rebateAmount
                ).reversed()
        );

        log.debug(
                "Available rebates calculated: orderNumber={}, candidateCount={}, eligibleCount={}",
                orderNumber,
                candidates.size(),
                available.size()
        );

        return available;
    }

    // =========================================================
    // SCOPE
    // =========================================================

    private boolean isScopeEligible(
            Rebate rebate,
            Order order
    ) {

        if (rebate.getScope()
                == RebateScope.GENERAL) {

            return true;
        }

        if (rebate.getScope()
                == RebateScope.CUSTOMER) {

            String phone =
                    normalizePhone(
                            order.getCustomerPhone()
                    );

            return rebateCustomerRepository
                    .existsByRebateIdAndCustomerPhone(
                            rebate.getId(),
                            phone
                    );
        }

        return false;
    }

    // =========================================================
    // USAGE LIMITS
    // =========================================================

    private boolean isUsageEligible(
            Rebate rebate,
            Order order
    ) {

        if (rebate.getMaxTotalUses()
                != null) {

            long totalUses =
                    rebateRedemptionRepository
                            .countByRebateId(
                                    rebate.getId()
                            );

            if (totalUses
                    >= rebate.getMaxTotalUses()) {

                return false;
            }
        }

        if (rebate.getMaxUsesPerCustomer()
                != null) {

            String phone =
                    normalizePhone(
                            order.getCustomerPhone()
                    );

            long customerUses =
                    rebateRedemptionRepository
                            .countByRebateIdAndCustomerPhone(
                                    rebate.getId(),
                                    phone
                            );

            if (customerUses
                    >= rebate.getMaxUsesPerCustomer()) {

                return false;
            }
        }

        return true;
    }

    // =========================================================
    // CALCULATION
    // =========================================================

    private AvailableRebateResponse calculateAvailableRebate(
            Rebate rebate,
            BigDecimal eligibleAmount
    ) {

        return switch (rebate.getRebateType()) {

            case PERCENTAGE ->
                    calculatePercentage(
                            rebate,
                            eligibleAmount
                    );

            case FIXED_AMOUNT ->
                    calculateFixed(
                            rebate,
                            eligibleAmount
                    );

            case SLAB ->
                    calculateSlab(
                            rebate,
                            eligibleAmount
                    );
        };
    }

    // =========================================================
    // PERCENTAGE
    // =========================================================

    private AvailableRebateResponse calculatePercentage(
            Rebate rebate,
            BigDecimal eligibleAmount
    ) {

        if (!meetsMinimumAmount(
                eligibleAmount,
                rebate.getMinimumOrderAmount()
        )) {

            return null;
        }

        BigDecimal rebateAmount =
                eligibleAmount
                        .multiply(
                                rebate.getRebateValue()
                        )
                        .divide(
                                BigDecimal.valueOf(100),
                                2,
                                RoundingMode.HALF_UP
                        );

        if (rebate.getMaximumDiscountAmount()
                != null) {

            rebateAmount =
                    rebateAmount.min(
                            rebate.getMaximumDiscountAmount()
                    );
        }

        rebateAmount =
                rebateAmount.min(
                        eligibleAmount
                );

        return response(
                rebate,
                eligibleAmount,
                rebateAmount,
                null,
                null,
                null
        );
    }

    // =========================================================
    // FIXED
    // =========================================================

    private AvailableRebateResponse calculateFixed(
            Rebate rebate,
            BigDecimal eligibleAmount
    ) {

        if (!meetsMinimumAmount(
                eligibleAmount,
                rebate.getMinimumOrderAmount()
        )) {

            return null;
        }

        BigDecimal rebateAmount =
                rebate.getRebateValue()
                        .min(
                                eligibleAmount
                        );

        return response(
                rebate,
                eligibleAmount,
                rebateAmount,
                null,
                null,
                null
        );
    }

    // =========================================================
    // SLAB
    // =========================================================

    private AvailableRebateResponse calculateSlab(
            Rebate rebate,
            BigDecimal eligibleAmount
    ) {

        List<RebateSlab> slabs =
                rebateSlabRepository
                        .findByRebateIdOrderByMinimumOrderAmountAsc(
                                rebate.getId()
                        );

        if (slabs.isEmpty()) {

            return null;
        }

        RebateSlab qualifyingSlab =
                null;

        RebateSlab nextSlab =
                null;

        for (RebateSlab slab : slabs) {

            if (eligibleAmount.compareTo(
                    slab.getMinimumOrderAmount()
            ) >= 0) {

                qualifyingSlab =
                        slab;

            } else {

                /*
                 * First threshold above the current
                 * order value is the next unlock.
                 */
                nextSlab =
                        slab;

                break;
            }
        }

        /*
         * Customer hasn't reached the first slab.
         */
        if (qualifyingSlab == null) {

            return null;
        }

        BigDecimal rebateAmount =
                qualifyingSlab
                        .getRebateAmount()
                        .min(
                                eligibleAmount
                        );

        BigDecimal nextMinimum =
                null;

        BigDecimal nextRebate =
                null;

        BigDecimal amountNeeded =
                null;

        if (nextSlab != null) {

            nextMinimum =
                    nextSlab
                            .getMinimumOrderAmount();

            nextRebate =
                    nextSlab
                            .getRebateAmount();

            amountNeeded =
                    nextMinimum
                            .subtract(
                                    eligibleAmount
                            )
                            .max(
                                    BigDecimal.ZERO
                            )
                            .setScale(
                                    2,
                                    RoundingMode.HALF_UP
                            );
        }

        return response(
                rebate,
                eligibleAmount,
                rebateAmount,
                nextMinimum,
                nextRebate,
                amountNeeded
        );
    }

    // =========================================================
    // RESPONSE
    // =========================================================

    private AvailableRebateResponse response(
            Rebate rebate,
            BigDecimal eligibleAmount,
            BigDecimal rebateAmount,
            BigDecimal nextMinimum,
            BigDecimal nextRebate,
            BigDecimal amountNeeded
    ) {

        BigDecimal normalizedRebate =
                money(rebateAmount);

        BigDecimal payableAfterRebate =
                eligibleAmount
                        .subtract(
                                normalizedRebate
                        )
                        .max(
                                BigDecimal.ZERO
                        )
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        return new AvailableRebateResponse(
                rebate.getId(),
                rebate.getCode(),
                rebate.getName(),
                rebate.getDescription(),
                rebate.getScope(),
                rebate.getRebateType(),
                normalizedRebate,
                payableAfterRebate,
                rebate.getMinimumOrderAmount(),
                rebate.getMaximumDiscountAmount(),
                nextMinimum,
                nextRebate,
                amountNeeded
        );
    }

    // =========================================================
    // ORDER VALUE
    // =========================================================

    private BigDecimal calculateEligibleAmount(
            Order order
    ) {

        BigDecimal amount =
                defaultZero(
                        order.getSubtotal()
                )
                        .add(
                                defaultZero(
                                        order.getTaxAmount()
                                )
                        )
                        .add(
                                defaultZero(
                                        order.getPriorityCharge()
                                )
                        );

        return money(amount);
    }

    private boolean meetsMinimumAmount(
            BigDecimal orderAmount,
            BigDecimal minimumAmount
    ) {

        return minimumAmount == null
                || orderAmount.compareTo(
                minimumAmount
        ) >= 0;
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private BigDecimal money(
            BigDecimal value
    ) {

        return value.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal defaultZero(
            BigDecimal value
    ) {

        return value == null
                ? BigDecimal.ZERO
                : value;
    }

    private String normalizePhone(
            String phone
    ) {

        if (phone == null) {
            return "";
        }

        return phone
                .trim()
                .replaceAll(
                        "\\s+",
                        ""
                );
    }

    @Transactional(readOnly = true)
    public AvailableRebateResponse getEligibleRebate(
            Order order,
            String rebateCode
    ) {

        if (order.getOrderStatus()
                != OrderStatus.PENDING_PAYMENT) {

            throw new IllegalStateException(
                    "Rebate can only be applied before payment."
            );
        }

        if (paymentRepository.existsByOrderId(
                order.getId()
        )) {

            throw new IllegalStateException(
                    "Rebate cannot be changed after payment processing has started."
            );
        }

        String normalizedCode =
                rebateCode
                        .trim()
                        .toUpperCase();

        Rebate rebate =
                rebateRepository
                        .findByCodeIgnoreCase(
                                normalizedCode
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Rebate does not exist."
                                )
                        );

        LocalDateTime now =
                LocalDateTime.now(
                        BUSINESS_ZONE
                );

        if (!rebate.isActive()) {

            throw new IllegalStateException(
                    "This rebate is not active."
            );
        }

        if (now.isBefore(
                rebate.getValidFrom()
        )) {

            throw new IllegalStateException(
                    "This rebate is not active yet."
            );
        }

        if (now.isAfter(
                rebate.getValidUntil()
        )) {

            throw new IllegalStateException(
                    "This rebate has expired."
            );
        }

        /*
         * Global rebate:
         * branch == null
         *
         * Branch rebate:
         * must match the order branch.
         */
        if (rebate.getBranch() != null
                && !rebate.getBranch()
                .getId()
                .equals(
                        order.getBranch()
                                .getId()
                )) {

            throw new IllegalStateException(
                    "This rebate is not available for this branch."
            );
        }

        if (!isScopeEligible(
                rebate,
                order
        )) {

            throw new IllegalStateException(
                    "This rebate is not available for this customer."
            );
        }

        if (!isUsageEligible(
                rebate,
                order
        )) {

            throw new IllegalStateException(
                    "Rebate usage limit has been reached."
            );
        }

        BigDecimal eligibleAmount =
                calculateEligibleAmount(order);

        AvailableRebateResponse response =
                calculateAvailableRebate(
                        rebate,
                        eligibleAmount
                );

        if (response == null) {

            throw new IllegalStateException(
                    "Order amount does not meet the minimum requirement for this rebate."
            );
        }

        return response;
    }
}
