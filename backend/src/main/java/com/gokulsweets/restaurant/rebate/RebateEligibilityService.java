package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.customer.identity.CustomerVisitPolicy.SelectionMode;
import com.gokulsweets.restaurant.observability.MethodTiming;
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

/** Coordinates rebate eligibility operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RebateEligibilityService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private com.gokulsweets.restaurant.customer.identity.CustomerVisitPolicy visits;

    /**
     * Updates visit policy.
     *
     * @param policy the policy
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void setVisitPolicy(
            com.gokulsweets.restaurant.customer.identity.CustomerVisitPolicy policy) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateEligibilityService.class,
                        "setVisitPolicy(com.gokulsweets.restaurant.customer.identity.CustomerVisitPolicy)");
        try {
            visits = policy;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "setVisitPolicy(com.gokulsweets.restaurant.customer.identity.CustomerVisitPolicy)");
        }
    }

    private final OrderRepository orderRepository;

    private final PaymentRepository paymentRepository;

    private final RebateRepository rebateRepository;

    private final RebateSlabRepository rebateSlabRepository;

    private final RebateCustomerRepository rebateCustomerRepository;

    private final RebateRedemptionRepository rebateRedemptionRepository;

    /** Immutable public tier data contract. */
    public record PublicTier(BigDecimal minimumOrderAmount, BigDecimal rebateAmount) {}

    /** Immutable public offer data contract. */
    public record PublicOffer(
            Long rebateId,
            String code,
            String name,
            String description,
            RebateType rebateType,
            BigDecimal rebateValue,
            BigDecimal minimumOrderAmount,
            BigDecimal maximumDiscountAmount,
            List<PublicTier> tiers,
            java.time.Instant validUntil) {}

    /**
     * Advertise unrestricted public terms without exposing customer-specific codes or eligibility.
     */
    @Transactional(readOnly = true)
    public List<PublicOffer> publicOffers(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateEligibilityService.class, "publicOffers(long)");
        try {
            if (branchId <= 0) throw new IllegalArgumentException("Choose a valid branch.");
            var candidates =
                    rebateRepository
                            .findActivePublicCandidates(branchId, LocalDateTime.now(BUSINESS_ZONE))
                            .stream()
                            .filter(r -> r.getScope() == RebateScope.GENERAL)
                            .toList();
            var visitEligible = visitEligibleOffers(candidates, new Order(), SelectionMode.PREVIEW);
            return candidates.stream()
                    .filter(r -> visitEligible.contains(r.getId()))
                    .filter(
                            r ->
                                    r.getMaxTotalUses() == null
                                            || rebateRedemptionRepository.countByRebateId(r.getId())
                                                    < r.getMaxTotalUses())
                    .map(
                            r ->
                                    new PublicOffer(
                                            r.getId(),
                                            r.getCode(),
                                            r.getName(),
                                            r.getDescription(),
                                            r.getRebateType(),
                                            r.getRebateType() == RebateType.FIXED_AMOUNT
                                                            && r.getMaximumDiscountAmount() != null
                                                    ? r.getRebateValue()
                                                            .min(r.getMaximumDiscountAmount())
                                                    : r.getRebateValue(),
                                            defaultZero(r.getMinimumOrderAmount()),
                                            r.getMaximumDiscountAmount(),
                                            publicTiers(r),
                                            r.getValidUntil() == null
                                                    ? null
                                                    : r.getValidUntil()
                                                            .atZone(BUSINESS_ZONE)
                                                            .toInstant()))
                    .toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "publicOffers(long)");
        }
    }

    /**
     * Publics tiers.
     *
     * @param rebate the rebate
     * @return the public tiers result
     */
    private List<PublicTier> publicTiers(Rebate rebate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateEligibilityService.class, "publicTiers(Rebate)");
        try {
            if (rebate.getRebateType() != RebateType.SLAB) return List.of();
            var tiers = new java.util.TreeMap<BigDecimal, BigDecimal>();
            for (var slab :
                    rebateSlabRepository.findByRebateIdOrderByMinimumOrderAmountAsc(
                            rebate.getId())) {
                var minimum =
                        slab.getMinimumOrderAmount()
                                .max(defaultZero(rebate.getMinimumOrderAmount()));
                var saving = slab.getRebateAmount().min(minimum);
                if (rebate.getMaximumDiscountAmount() != null)
                    saving = saving.min(rebate.getMaximumDiscountAmount());
                // Match checkout: the last qualifying configured slab wins.
                tiers.put(minimum, saving);
            }
            return tiers.entrySet().stream()
                    .map(t -> new PublicTier(t.getKey(), t.getValue()))
                    .toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "publicTiers(Rebate)");
        }
    }

    /**
     * Returns available rebates.
     *
     * @param orderNumber the order number
     * @return the get available rebates result
     */
    @Transactional(readOnly = true)
    public List<AvailableRebateResponse> getAvailableRebates(String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateEligibilityService.class, "getAvailableRebates(String)");
        try {
            Order order =
                    orderRepository
                            .findDetailedByOrderNumber(orderNumber)
                            .orElseThrow(
                                    () -> new IllegalArgumentException("Order does not exist."));
            /*
             * Rebates can only be selected while
             * waiting for payment.
             */
            if (order.getOrderStatus() != OrderStatus.PENDING_PAYMENT) {
                throw new IllegalStateException("Rebates are only available before payment.");
            }
            /*
             * Once payment processing starts, the amount
             * must no longer change.
             */
            if (paymentRepository.existsByOrderId(order.getId())) {
                throw new IllegalStateException(
                        "Rebate cannot be selected after payment processing has started.");
            }
            return previewDraft(order);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "getAvailableRebates(String)");
        }
    }

    /** Prices a validated draft without creating an order or claiming an offer. */
    @Transactional(readOnly = true)
    public List<AvailableRebateResponse> previewDraft(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateEligibilityService.class, "previewDraft(Order)");
        try {
            BigDecimal eligibleAmount = calculateEligibleAmount(order);
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            List<Rebate> candidates =
                    rebateRepository.findActivePublicCandidates(order.getBranch().getId(), now);
            List<AvailableRebateResponse> available = new ArrayList<>();
            var visitEligible = visitEligibleOffers(candidates, order, SelectionMode.PREVIEW);
            for (Rebate rebate : candidates) {
                if (!isScopeEligible(rebate, order, visitEligible)) {
                    continue;
                }
                if (!isUsageEligible(rebate, order)) {
                    continue;
                }
                AvailableRebateResponse response = calculateAvailableRebate(rebate, eligibleAmount);
                /*
                 * Null means minimum spend/slab threshold
                 * has not been reached.
                 */
                if (response != null) {
                    available.add(withFee(response, order));
                }
            }
            /*
             * Show the highest-value rebate first.
             */
            available.sort(Comparator.comparing(AvailableRebateResponse::rebateAmount).reversed());
            log.debug(
                    "Available rebates calculated: orderNumber={}, candidateCount={},"
                            + " eligibleCount={}",
                    order.getOrderNumber(),
                    candidates.size(),
                    available.size());
            return available;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "previewDraft(Order)");
        }
    }

    /** Informational targets are separate from rebates that may currently be applied. */
    @Transactional(readOnly = true)
    public List<AvailableRebateResponse> getSpendTargets(String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateEligibilityService.class, "getSpendTargets(String)");
        try {
            // also enforces unpaid/order lifecycle
            var available = getAvailableRebates(orderNumber);
            var order = orderRepository.findDetailedByOrderNumber(orderNumber).orElseThrow();
            return previewSpendTargets(order, available);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "getSpendTargets(String)");
        }
    }

    /**
     * Previews spend targets.
     *
     * @param order the order
     * @param available the available
     * @return the preview spend targets result
     */
    @Transactional(readOnly = true)
    public List<AvailableRebateResponse> previewSpendTargets(
            Order order, List<AvailableRebateResponse> available) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateEligibilityService.class,
                        "previewSpendTargets(Order,List<AvailableRebateResponse>)");
        try {
            if (order.getPickupType() != com.gokulsweets.restaurant.order.enums.PickupType.NORMAL
                    || order.getFulfillmentType()
                            != com.gokulsweets.restaurant.order.enums.FulfillmentType.PICKUP)
                return List.of();
            var eligible = calculateEligibleAmount(order);
            var baseline =
                    available.stream()
                            .map(AvailableRebateResponse::rebateAmount)
                            .max(BigDecimal::compareTo)
                            .orElse(BigDecimal.ZERO)
                            .max(defaultZero(order.getRebateDiscountAmount()));
            var targets = new ArrayList<AvailableRebateResponse>();
            var candidates =
                    rebateRepository.findActivePublicCandidates(
                            order.getBranch().getId(), LocalDateTime.now(BUSINESS_ZONE));
            var visitEligible = visitEligibleOffers(candidates, order, SelectionMode.PREVIEW);
            for (var rebate : candidates) {
                if (!isScopeEligible(rebate, order, visitEligible)
                        || !isUsageEligible(rebate, order)) continue;
                if (rebate.getRebateType() != RebateType.SLAB) continue;
                for (var slab :
                        rebateSlabRepository.findByRebateIdOrderByMinimumOrderAmountAsc(
                                rebate.getId())) {
                    var threshold =
                            slab.getMinimumOrderAmount()
                                    .max(defaultZero(rebate.getMinimumOrderAmount()));
                    if (threshold.compareTo(eligible) <= 0) continue;
                    var saving = slab.getRebateAmount().min(threshold);
                    if (rebate.getMaximumDiscountAmount() != null)
                        saving = saving.min(rebate.getMaximumDiscountAmount());
                    var needed = threshold.subtract(eligible);
                    if (saving.compareTo(baseline) > 0
                            && saving.subtract(baseline).compareTo(needed) < 0) {
                        var target =
                                response(rebate, eligible, baseline, threshold, saving, needed);
                        if (target.amountNeededForNextSlab() != null)
                            targets.add(withFee(target, order));
                        break;
                    }
                }
            }
            return targets;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "previewSpendTargets(Order,List<AvailableRebateResponse>)");
        }
    }

    // =========================================================
    // SCOPE
    // =========================================================
    /**
     * Visits eligible offers.
     *
     * @param candidates the candidates
     * @param order the order
     * @param mode the mode
     * @return the visit eligible offers result
     */
    private java.util.Set<Long> visitEligibleOffers(
            List<Rebate> candidates, Order order, SelectionMode mode) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateEligibilityService.class,
                        "visitEligibleOffers(List<Rebate>,Order,SelectionMode)");
        try {
            var ids = candidates.stream().map(Rebate::getId).toList();
            return visits == null
                    ? new java.util.HashSet<>(ids)
                    : visits.eligibleOffers(ids, order, mode);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "visitEligibleOffers(List<Rebate>,Order,SelectionMode)");
        }
    }

    /**
     * Reports whether scope eligible.
     *
     * @param rebate the rebate
     * @param order the order
     * @param visitEligible the visit eligible
     * @return the is scope eligible result
     */
    private boolean isScopeEligible(Rebate rebate, Order order, java.util.Set<Long> visitEligible) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateEligibilityService.class,
                        "isScopeEligible(Rebate,Order,java.util.Set<Long>)");
        try {
            if (!visitEligible.contains(rebate.getId())) return false;
            if (rebate.getScope() == RebateScope.GENERAL) {
                return true;
            }
            if (rebate.getScope() == RebateScope.CUSTOMER) {
                String phone = normalizePhone(order.getCustomerPhone());
                return rebateCustomerRepository.existsByRebateIdAndCustomerPhone(
                        rebate.getId(), phone);
            }
            return false;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "isScopeEligible(Rebate,Order,java.util.Set<Long>)");
        }
    }

    // =========================================================
    // USAGE LIMITS
    // =========================================================
    /**
     * Reports whether usage eligible.
     *
     * @param rebate the rebate
     * @param order the order
     * @return the is usage eligible result
     */
    private boolean isUsageEligible(Rebate rebate, Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateEligibilityService.class, "isUsageEligible(Rebate,Order)");
        try {
            if (rebate.getMaxTotalUses() != null) {
                long totalUses = rebateRedemptionRepository.countByRebateId(rebate.getId());
                if (totalUses >= rebate.getMaxTotalUses()) {
                    return false;
                }
            }
            if (rebate.getMaxUsesPerCustomer() != null) {
                String phone = normalizePhone(order.getCustomerPhone());
                long customerUses =
                        rebateRedemptionRepository.countByRebateIdAndCustomerPhone(
                                rebate.getId(), phone);
                if (customerUses >= rebate.getMaxUsesPerCustomer()) {
                    return false;
                }
            }
            return true;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "isUsageEligible(Rebate,Order)");
        }
    }

    // =========================================================
    // CALCULATION
    // =========================================================
    /**
     * Calculates available rebate.
     *
     * @param rebate the rebate
     * @param eligibleAmount the eligible amount
     * @return the calculate available rebate result
     */
    private AvailableRebateResponse calculateAvailableRebate(
            Rebate rebate, BigDecimal eligibleAmount) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateEligibilityService.class,
                        "calculateAvailableRebate(Rebate,BigDecimal)");
        try {
            return switch (rebate.getRebateType()) {
                case PERCENTAGE -> calculatePercentage(rebate, eligibleAmount);
                case FIXED_AMOUNT -> calculateFixed(rebate, eligibleAmount);
                case SLAB -> calculateSlab(rebate, eligibleAmount);
            };
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "calculateAvailableRebate(Rebate,BigDecimal)");
        }
    }

    // =========================================================
    // PERCENTAGE
    // =========================================================
    /**
     * Calculates percentage.
     *
     * @param rebate the rebate
     * @param eligibleAmount the eligible amount
     * @return the calculate percentage result
     */
    private AvailableRebateResponse calculatePercentage(Rebate rebate, BigDecimal eligibleAmount) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateEligibilityService.class, "calculatePercentage(Rebate,BigDecimal)");
        try {
            if (!meetsMinimumAmount(eligibleAmount, rebate.getMinimumOrderAmount())) {
                return null;
            }
            BigDecimal rebateAmount =
                    eligibleAmount
                            .multiply(rebate.getRebateValue())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (rebate.getMaximumDiscountAmount() != null) {
                rebateAmount = rebateAmount.min(rebate.getMaximumDiscountAmount());
            }
            rebateAmount = rebateAmount.min(eligibleAmount);
            return response(rebate, eligibleAmount, rebateAmount, null, null, null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "calculatePercentage(Rebate,BigDecimal)");
        }
    }

    // =========================================================
    // FIXED
    // =========================================================
    /**
     * Calculates fixed.
     *
     * @param rebate the rebate
     * @param eligibleAmount the eligible amount
     * @return the calculate fixed result
     */
    private AvailableRebateResponse calculateFixed(Rebate rebate, BigDecimal eligibleAmount) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateEligibilityService.class, "calculateFixed(Rebate,BigDecimal)");
        try {
            if (!meetsMinimumAmount(eligibleAmount, rebate.getMinimumOrderAmount())) {
                return null;
            }
            BigDecimal rebateAmount = rebate.getRebateValue().min(eligibleAmount);
            return response(rebate, eligibleAmount, rebateAmount, null, null, null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "calculateFixed(Rebate,BigDecimal)");
        }
    }

    // =========================================================
    // SLAB
    // =========================================================
    /**
     * Calculates slab.
     *
     * @param rebate the rebate
     * @param eligibleAmount the eligible amount
     * @return the calculate slab result
     */
    private AvailableRebateResponse calculateSlab(Rebate rebate, BigDecimal eligibleAmount) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateEligibilityService.class, "calculateSlab(Rebate,BigDecimal)");
        try {
            if (!meetsMinimumAmount(eligibleAmount, rebate.getMinimumOrderAmount())) return null;
            List<RebateSlab> slabs =
                    rebateSlabRepository.findByRebateIdOrderByMinimumOrderAmountAsc(rebate.getId());
            if (slabs.isEmpty()) {
                return null;
            }
            RebateSlab qualifyingSlab = null;
            RebateSlab nextSlab = null;
            for (RebateSlab slab : slabs) {
                if (eligibleAmount.compareTo(slab.getMinimumOrderAmount()) >= 0) {
                    qualifyingSlab = slab;
                } else {
                    /*
                     * First threshold above the current
                     * order value is the next unlock.
                     */
                    nextSlab = slab;
                    break;
                }
            }
            /*
             * Customer hasn't reached the first slab.
             */
            if (qualifyingSlab == null) {
                return null;
            }
            BigDecimal rebateAmount = qualifyingSlab.getRebateAmount().min(eligibleAmount);
            BigDecimal nextMinimum = null;
            BigDecimal nextRebate = null;
            BigDecimal amountNeeded = null;
            if (nextSlab != null) {
                nextMinimum = nextSlab.getMinimumOrderAmount();
                nextRebate = nextSlab.getRebateAmount();
                amountNeeded =
                        nextMinimum
                                .subtract(eligibleAmount)
                                .max(BigDecimal.ZERO)
                                .setScale(2, RoundingMode.HALF_UP);
            }
            return response(
                    rebate, eligibleAmount, rebateAmount, nextMinimum, nextRebate, amountNeeded);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "calculateSlab(Rebate,BigDecimal)");
        }
    }

    // =========================================================
    // RESPONSE
    // =========================================================
    /**
     * Responses the operation.
     *
     * @param rebate the rebate
     * @param eligibleAmount the eligible amount
     * @param rebateAmount the rebate amount
     * @param nextMinimum the next minimum
     * @param nextRebate the next rebate
     * @param amountNeeded the amount needed
     * @return the response result
     */
    private AvailableRebateResponse response(
            Rebate rebate,
            BigDecimal eligibleAmount,
            BigDecimal rebateAmount,
            BigDecimal nextMinimum,
            BigDecimal nextRebate,
            BigDecimal amountNeeded) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateEligibilityService.class,
                        "response(Rebate,BigDecimal,BigDecimal,BigDecimal,BigDecimal,BigDecimal)");
        try {
            BigDecimal normalizedRebate = money(rebateAmount.min(eligibleAmount));
            if (rebate.getMaximumDiscountAmount() != null)
                normalizedRebate = normalizedRebate.min(rebate.getMaximumDiscountAmount());
            if (nextRebate != null) {
                if (rebate.getMaximumDiscountAmount() != null)
                    nextRebate = nextRebate.min(rebate.getMaximumDiscountAmount());
                if (nextMinimum != null) nextRebate = nextRebate.min(nextMinimum);
                // Protect incremental revenue; costs are unavailable, so this is not a profit test.
                if (amountNeeded == null
                        || nextRebate.subtract(normalizedRebate).signum() <= 0
                        || nextRebate.subtract(normalizedRebate).compareTo(amountNeeded) >= 0) {
                    nextMinimum = null;
                    nextRebate = null;
                    amountNeeded = null;
                }
            }
            BigDecimal payableAfterRebate =
                    eligibleAmount
                            .subtract(normalizedRebate)
                            .max(BigDecimal.ZERO)
                            .setScale(2, RoundingMode.HALF_UP);
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
                    amountNeeded);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "response(Rebate,BigDecimal,BigDecimal,BigDecimal,BigDecimal,BigDecimal)");
        }
    }

    // =========================================================
    // ORDER VALUE
    // =========================================================
    /**
     * Withs fee.
     *
     * @param r the r
     * @param order the order
     * @return the with fee result
     */
    private AvailableRebateResponse withFee(AvailableRebateResponse r, Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateEligibilityService.class, "withFee(AvailableRebateResponse,Order)");
        try {
            return new AvailableRebateResponse(
                    r.rebateId(),
                    r.code(),
                    r.name(),
                    r.description(),
                    r.scope(),
                    r.rebateType(),
                    r.rebateAmount(),
                    com.gokulsweets.restaurant.order.service.PaymentFeePricing.totalWithFee(
                            order,
                            r.payableAfterRebate()
                                    .add(
                                            order.isLoyaltyEnrolled()
                                                    ? defaultZero(order.getTaxAmount())
                                                            .add(
                                                                    defaultZero(
                                                                            order
                                                                                    .getPriorityCharge()))
                                                    : BigDecimal.ZERO)
                                    .add(defaultZero(order.getConvenienceFee()))
                                    .add(defaultZero(order.getDeliveryFee()))),
                    r.minimumOrderAmount(),
                    r.maximumDiscountAmount(),
                    r.nextSlabMinimumOrderAmount(),
                    r.nextSlabRebateAmount(),
                    r.amountNeededForNextSlab());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "withFee(AvailableRebateResponse,Order)");
        }
    }

    /**
     * Calculates eligible amount.
     *
     * @param order the order
     * @return the calculate eligible amount result
     */
    private BigDecimal calculateEligibleAmount(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateEligibilityService.class, "calculateEligibleAmount(Order)");
        try {
            if (order.isLoyaltyEnrolled())
                return money(
                        defaultZero(order.getSubtotal())
                                .subtract(defaultZero(order.getLoyaltyDiscount()))
                                .max(BigDecimal.ZERO));
            BigDecimal amount =
                    defaultZero(order.getSubtotal())
                            .add(defaultZero(order.getTaxAmount()))
                            .add(defaultZero(order.getPriorityCharge()));
            return money(amount);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "calculateEligibleAmount(Order)");
        }
    }

    /**
     * Meetses minimum amount.
     *
     * @param orderAmount the order amount
     * @param minimumAmount the minimum amount
     * @return the meets minimum amount result
     */
    private boolean meetsMinimumAmount(BigDecimal orderAmount, BigDecimal minimumAmount) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateEligibilityService.class,
                        "meetsMinimumAmount(BigDecimal,BigDecimal)");
        try {
            return minimumAmount == null || orderAmount.compareTo(minimumAmount) >= 0;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "meetsMinimumAmount(BigDecimal,BigDecimal)");
        }
    }

    // =========================================================
    // HELPERS
    // =========================================================
    /**
     * Money the operation.
     *
     * @param value the value
     * @return the money result
     */
    private BigDecimal money(BigDecimal value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateEligibilityService.class, "money(BigDecimal)");
        try {
            return value.setScale(2, RoundingMode.HALF_UP);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RebateEligibilityService.class, "money(BigDecimal)");
        }
    }

    /**
     * Defaults zero.
     *
     * @param value the value
     * @return the default zero result
     */
    private BigDecimal defaultZero(BigDecimal value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateEligibilityService.class, "defaultZero(BigDecimal)");
        try {
            return value == null ? BigDecimal.ZERO : value;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "defaultZero(BigDecimal)");
        }
    }

    /**
     * Normalizes phone.
     *
     * @param phone the phone
     * @return the normalize phone result
     */
    private String normalizePhone(String phone) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateEligibilityService.class, "normalizePhone(String)");
        try {
            if (phone == null) {
                return "";
            }
            return phone.trim().replaceAll("\\s+", "");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "normalizePhone(String)");
        }
    }

    /** An ineligible saved choice is expected during reward changes, not a failed transaction. */
    @Transactional
    public java.util.Optional<AvailableRebateResponse> findEligibleRebate(
            Order order, String code) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateEligibilityService.class, "findEligibleRebate(Order,String)");
        try {
            try {
                return java.util.Optional.of(getEligibleRebate(order, code));
            } catch (IllegalArgumentException | IllegalStateException ineligible) {
                return java.util.Optional.empty();
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "findEligibleRebate(Order,String)");
        }
    }

    /**
     * Explicit-code pricing for an unsaved validated draft; persisted orders retain payment guards.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<AvailableRebateResponse> findEligibleDraftRebate(
            Order draft, String code) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateEligibilityService.class, "findEligibleDraftRebate(Order,String)");
        try {
            if (draft.getId() != null)
                throw new IllegalArgumentException("Draft pricing requires an unsaved order.");
            try {
                return java.util.Optional.of(priceEligibleCode(draft, code, SelectionMode.PREVIEW));
            } catch (IllegalArgumentException | IllegalStateException ineligible) {
                return java.util.Optional.empty();
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "findEligibleDraftRebate(Order,String)");
        }
    }

    /**
     * Returns eligible rebate.
     *
     * @param order the order
     * @param rebateCode the rebate code
     * @return the get eligible rebate result
     */
    @Transactional
    public AvailableRebateResponse getEligibleRebate(Order order, String rebateCode) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateEligibilityService.class, "getEligibleRebate(Order,String)");
        try {
            if (order.getOrderStatus() != OrderStatus.PENDING_PAYMENT) {
                throw new IllegalStateException("Rebate can only be applied before payment.");
            }
            if (paymentRepository.existsByOrderId(order.getId())) {
                throw new IllegalStateException(
                        "Rebate cannot be changed after payment processing has started.");
            }
            return priceEligibleCode(order, rebateCode, SelectionMode.ACCEPTANCE);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "getEligibleRebate(Order,String)");
        }
    }

    /**
     * Prices eligible code.
     *
     * @param order the order
     * @param rebateCode the rebate code
     * @param mode the mode
     * @return the price eligible code result
     */
    private AvailableRebateResponse priceEligibleCode(
            Order order, String rebateCode, SelectionMode mode) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateEligibilityService.class,
                        "priceEligibleCode(Order,String,SelectionMode)");
        try {
            String normalizedCode = rebateCode.trim().toUpperCase();
            Rebate rebate =
                    rebateRepository
                            .findByCodeIgnoreCase(normalizedCode)
                            .orElseThrow(
                                    () -> new IllegalArgumentException("Rebate does not exist."));
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            if (!rebate.isActive()) {
                throw new IllegalStateException("This rebate is not active.");
            }
            if (now.isBefore(rebate.getValidFrom())) {
                throw new IllegalStateException("This rebate is not active yet.");
            }
            if (now.isAfter(rebate.getValidUntil())) {
                throw new IllegalStateException("This rebate has expired.");
            }
            /*
             * Global rebate:
             * branch == null
             *
             * Branch rebate:
             * must match the order branch.
             */
            if (rebate.getBranch() != null
                    && !rebate.getBranch().getId().equals(order.getBranch().getId())) {
                throw new IllegalStateException("This rebate is not available for this branch.");
            }
            if (!isScopeEligible(
                    rebate, order, visitEligibleOffers(List.of(rebate), order, mode))) {
                throw new IllegalStateException("This rebate is not available for this customer.");
            }
            if (!isUsageEligible(rebate, order)) {
                throw new IllegalStateException("Rebate usage limit has been reached.");
            }
            BigDecimal eligibleAmount = calculateEligibleAmount(order);
            AvailableRebateResponse response = calculateAvailableRebate(rebate, eligibleAmount);
            if (response == null) {
                throw new IllegalStateException(
                        "Order amount does not meet the minimum requirement for this rebate.");
            }
            return withFee(response, order);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateEligibilityService.class,
                    "priceEligibleCode(Order,String,SelectionMode)");
        }
    }
}
