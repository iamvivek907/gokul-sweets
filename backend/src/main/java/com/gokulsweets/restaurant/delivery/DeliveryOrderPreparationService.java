package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.service.OrderCalculationService;
import com.gokulsweets.restaurant.order.service.OrderValidationService;
import com.gokulsweets.restaurant.order.service.model.OrderCalculationResult;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderData;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Internal, provisional price and eligibility check for a selected delivery window. */
@Service
@RequiredArgsConstructor
public class DeliveryOrderPreparationService {

    private final EnhancementProperties flags;

    private final DeliveryCapacityService capacity;

    private final BranchRepository branches;

    private final OrderValidationService validation;

    private final OrderCalculationService calculation;

    private final DeliveryEconomicsService economics;

    /**
     * Prepares the operation.
     *
     * @param request the request
     * @param windowId the window id
     * @return the prepare result
     */
    @Transactional(readOnly = true)
    public Prepared prepare(DeliveryCapacityService.QuoteRequest request, long windowId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DeliveryOrderPreparationService.class,
                        "prepare(DeliveryCapacityService.QuoteRequest,long)");
        try {
            if (!flags.isDeliveryRiderHolds()
                    || !flags.isDeliveryAddressBoundaries()
                    || !capacity.enabled())
                throw new IllegalStateException("Delivery order preparation is disabled.");
            if (request == null
                    || request.serviceDate() == null
                    || request.items() == null
                    || windowId <= 0)
                throw new IllegalArgumentException(
                        "Select a valid delivery date, cart and window.");
            var selected =
                    capacity.quote(request).provisionalWindows().stream()
                            .filter(window -> window.id() == windowId)
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Selected delivery window is unavailable."));
            var branch =
                    branches.findByIdAndActiveTrue(request.branchId())
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Selected branch is unavailable."));
            var items = validation.validateCart(branch.getId(), request.items());
            if (items.size() != request.items().size() || items.stream().anyMatch(Objects::isNull))
                throw new IllegalArgumentException("Review the items in your delivery cart.");
            var validated = new ValidatedOrderData(branch, null, null, items);
            boolean collectTax = calculation.collectingTax();
            var price = calculation.calculateDelivery(items, collectTax);
            var assessment = economics.assess(price);
            if (!assessment.viable()) throw new IllegalStateException(assessment.alternative());
            return new Prepared(
                    validated,
                    calculation.withPaymentFee(price, branch, assessment.fee(), collectTax),
                    selected,
                    assessment);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryOrderPreparationService.class,
                    "prepare(DeliveryCapacityService.QuoteRequest,long)");
        }
    }

    /**
     * Immutable prepared data contract.
     *
     * @param validated the validated
     * @param price the price
     * @param window the window
     * @param economics the economics
     */
    public record Prepared(
            ValidatedOrderData validated,
            OrderCalculationResult price,
            DeliveryCapacityService.Window window,
            DeliveryEconomicsService.Assessment economics) {}
}
