package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.customer.CustomerContact;
import com.gokulsweets.restaurant.customer.CustomerContactService;
import com.gokulsweets.restaurant.customer.identity.VerifiedOrderOwnership;
import com.gokulsweets.restaurant.inventory.service.OrderInventoryReservationService;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.dto.CreateOrderRequest;
import com.gokulsweets.restaurant.order.dto.OrderItemResponse;
import com.gokulsweets.restaurant.order.dto.OrderResponse;
import com.gokulsweets.restaurant.order.dto.UpdatePendingOrderRequest;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.entity.OrderItem;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.order.service.model.CalculatedOrderItem;
import com.gokulsweets.restaurant.order.service.model.OrderCalculationResult;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderData;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.pickup.service.PickupSlotReservationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Coordinates order operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    private final OrderRepository orderRepository;

    private final PaymentRepository paymentRepository;

    private final OrderValidationService orderValidationService;

    private final OrderCalculationService orderCalculationService;

    private final PickupSlotReservationService pickupSlotReservationService;

    private final OrderNumberGenerator orderNumberGenerator;

    private final OrderIdempotencyService orderIdempotencyService;

    private final CustomerContactService customerContactService;

    private final OrderInventoryReservationService orderInventoryReservationService;

    private final PickupCommitmentCheck pickupCommitmentCheck;

    private final CheckoutQuoteService checkoutQuoteService;

    private final VerifiedOrderOwnership verifiedOrderOwnership;

    private final com.gokulsweets.restaurant.loyalty.LoyaltyService loyalty;

    @Value("${checkout.reservation-expiry-minutes:15}")
    private long reservationExpiryMinutes;

    // =========================================================
    // CREATE ORDER
    // =========================================================
    /**
     * Creates order.
     *
     * @param request the request
     * @param idempotencyKey the idempotency key
     * @return the create order result
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderService.class, "createOrder(CreateOrderRequest,String)");
        try {
            return createOrder(request, idempotencyKey, null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderService.class,
                    "createOrder(CreateOrderRequest,String)");
        }
    }

    /**
     * Creates order.
     *
     * @param request the request
     * @param idempotencyKey the idempotency key
     * @param identityToken the identity token
     * @return the create order result
     */
    @Transactional
    public OrderResponse createOrder(
            CreateOrderRequest request, String idempotencyKey, String identityToken) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderService.class, "createOrder(CreateOrderRequest,String,String)");
        try {
            verifiedOrderOwnership.requireCheckoutIdentity(request.customerPhone(), identityToken);
            String requestHash = orderIdempotencyService.createRequestHash(request);
            OrderIdempotencyService.ClaimResult claim =
                    orderIdempotencyService.claim(idempotencyKey, requestHash);
            /*
             * Retry of an already completed request.
             *
             * Do NOT:
             *
             * - validate again
             * - reserve capacity again
             * - reset reservation expiry
             * - create another order
             */
            if (!claim.newRequest()) {
                Order existingOrder = claim.existingOrder();
                verifiedOrderOwnership.requireCheckoutReplay(existingOrder.getId(), identityToken);
                log.info(
                        "Duplicate order request resolved idempotently: orderId={}, orderNumber={}",
                        existingOrder.getId(),
                        existingOrder.getOrderNumber());
                return toResponse(existingOrder);
            }
            log.info(
                    "Starting order creation: branchId={}, pickupSlotId={}, pickupType={},"
                            + " itemCount={}",
                    request.branchId(),
                    request.pickupSlotId(),
                    request.pickupType(),
                    request.items().size());
            ValidatedOrderData validatedOrder = orderValidationService.validate(request);
            // A read-only whole-cart preflight; guarded slot update and locked inventory
            // holds below are still the source of truth when another order races us.
            pickupCommitmentCheck.checkNewOrder(validatedOrder, request.items());
            OrderCalculationResult calculation = orderCalculationService.calculate(validatedOrder);
            // Verify the exact accepted server price before creating any slot or stock hold.
            checkoutQuoteService.accept(request, null, calculation, request.quoteToken());
            reservePickupCapacity(validatedOrder);
            Order order = buildOrder(request, validatedOrder, calculation);
            addOrderItems(order, calculation);
            Order savedOrder = orderRepository.saveAndFlush(order);
            verifiedOrderOwnership.bindNewOrder(
                    savedOrder.getId(), request.customerPhone(), identityToken);
            loyalty.reserve(savedOrder, request.rewardCode());
            /*
             * Reserve the complete order inventory after the order
             * has received its database identity, but inside this
             * same transaction.
             *
             * If any product is unavailable, the inventory service
             * throws and Spring rolls back:
             *
             * - this order
             * - all order items
             * - pickup-slot capacity
             * - every inventory hold and ledger entry
             */
            orderInventoryReservationService.synchronizePendingOrder(savedOrder, validatedOrder);
            orderIdempotencyService.linkOrder(idempotencyKey, savedOrder);
            log.info(
                    "Order created successfully: orderId={}, orderNumber={}, branchId={},"
                        + " pickupSlotId={}, status={}, totalAmount={}, reservationExpiresAt={}",
                    savedOrder.getId(),
                    savedOrder.getOrderNumber(),
                    savedOrder.getBranch().getId(),
                    savedOrder.getPickupSlot().getId(),
                    savedOrder.getOrderStatus(),
                    savedOrder.getTotalAmount(),
                    savedOrder.getReservationExpiresAt());
            return toResponse(savedOrder);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderService.class,
                    "createOrder(CreateOrderRequest,String,String)");
        }
    }

    // =========================================================
    // UPDATE EXISTING PENDING CHECKOUT
    // =========================================================
    /**
     * Updates pending checkout.
     *
     * @param orderNumber the order number
     * @param request the request
     * @return the update pending checkout result
     */
    @Transactional
    public OrderResponse updatePendingCheckout(
            String orderNumber, UpdatePendingOrderRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderService.class,
                        "updatePendingCheckout(String,UpdatePendingOrderRequest)");
        try {
            log.info(
                    "Starting pending checkout update: orderNumber={}, requestedPickupSlotId={},"
                            + " requestedPickupType={}, itemCount={}",
                    orderNumber,
                    request.pickupSlotId(),
                    request.pickupType(),
                    request.items().size());
            /*
             * Lock the same order row used by:
             *
             * - payment creation
             * - rebate apply/remove
             * - checkout expiry
             *
             * Therefore payment creation cannot race
             * with this cart update.
             */
            Order order =
                    orderRepository
                            .findForUpdate(orderNumber)
                            .orElseThrow(
                                    () -> {
                                        log.warn(
                                                "Pending checkout update failed because order does"
                                                        + " not exist: orderNumber={}",
                                                orderNumber);
                                        return new IllegalArgumentException(
                                                "Order does not exist.");
                                    });
            validatePendingCheckoutCanBeUpdated(order);
            /*
             * Absolutely no Payment record may exist.
             *
             * This is intentionally stronger than checking
             * only PaymentStatus.PENDING.
             *
             * Once payment creation has ever started, the
             * payment lifecycle owns this order and its
             * amount must not be changed.
             */
            if (paymentRepository.existsByOrderId(order.getId())) {
                log.warn(
                        "Pending checkout update rejected because payment attempt exists:"
                                + " orderId={}, orderNumber={}",
                        order.getId(),
                        order.getOrderNumber());
                throw new IllegalStateException(
                        "This order can no longer be changed because payment processing has already"
                                + " started.");
            }
            if (order.getLoyaltyCoins() > 0)
                throw new IllegalStateException(
                        "Remove your reward before editing the reserved checkout.");
            Long previousPickupSlotId = order.getPickupSlot().getId();
            PickupType previousPickupType = order.getPickupType();
            /*
             * Keeping the same slot and pickup type reuses the
             * capacity already owned by this order. A changed
             * selection is validated as a new reservation.
             */
            ValidatedOrderData validatedOrder =
                    orderValidationService.validateExistingReservationUpdate(
                            order, request.pickupSlotId(), request.pickupType(), request.items());
            boolean pickupReservationChanged =
                    !previousPickupSlotId.equals(validatedOrder.pickupSlot().getId())
                            || previousPickupType != validatedOrder.pickupType();
            /*
             * Re-price everything from authoritative
             * backend product/branch/tax configuration.
             */
            OrderCalculationResult calculation = orderCalculationService.calculate(validatedOrder);
            // A pending checkout edit may also change tax, price or pickup charge.
            checkoutQuoteService.acceptUpdate(order, request, calculation);
            /*
             * Reserve first and release second. Both operations
             * participate in this transaction. A later failure
             * rolls back the whole transfer, preserving the old
             * reservation.
             */
            if (pickupReservationChanged) {
                reservePickupCapacity(validatedOrder);
                releasePickupCapacity(previousPickupSlotId, previousPickupType);
                order.setPickupSlot(validatedOrder.pickupSlot());
                order.setPickupType(validatedOrder.pickupType());
            }
            /*
             * Replace the old order item snapshots.
             *
             * orphanRemoval=true on Order.items means old
             * database order_items will be deleted when the
             * transaction flushes.
             */
            order.getItems().clear();
            addOrderItems(order, calculation);
            /*
             * Refresh authoritative monetary snapshots.
             *
             * This includes any changed priority charge.
             */
            order.setPaymentFee(calculation.paymentFee());
            order.setPaymentFeeTax(calculation.paymentFeeTax());
            order.setPaymentFeeRate(calculation.paymentFeeRate());
            order.setPaymentFeeTaxRate(calculation.paymentFeeTaxRate());
            order.setConvenienceFee(calculation.convenienceFee());
            order.setConvenienceFeeTax(calculation.convenienceFeeTax());
            order.setConvenienceFeeTaxRate(calculation.convenienceFeeTaxRate());
            order.setPriorityCharge(calculation.priorityCharge());
            order.setSubtotal(calculation.subtotal());
            order.setTaxAmount(calculation.taxAmount());
            /*
             * Cart contents or pickup selection changed.
             *
             * Any previously applied rebate was calculated
             * against the old order amount and therefore
             * must not silently survive.
             *
             * Clear it completely.
             *
             * The customer will return to Offers and the
             * backend will calculate current eligibility
             * again.
             */
            if (order.isLoyaltyEnrolled())
                order.setLoyaltyEligibleSubtotal(loyalty.eligibleSubtotal(order));
            clearAppliedRebate(order);
            order.setTotalAmount(calculation.totalAmount());
            /*
             * IMPORTANT:
             *
             * DO NOT change:
             *
             * orderNumber
             * branch
             * customer details
             * orderStatus
             * reservationExpiresAt
             *
             * Especially:
             *
             * NEVER restart the reservation timer here.
             */
            Order savedOrder = orderRepository.saveAndFlush(order);
            /*
             * Synchronize the whole reservation set atomically.
             *
             * This supports:
             *
             * - changed quantities or weights
             * - added/removed products
             * - a changed pickup date
             *
             * The original checkout expiry is deliberately reused.
             */
            orderInventoryReservationService.synchronizePendingOrder(
                    savedOrder, validatedOrder, pickupReservationChanged);
            log.info(
                    "Pending checkout updated successfully: orderId={}, orderNumber={},"
                            + " previousPickupSlotId={}, pickupSlotId={}, previousPickupType={},"
                            + " pickupType={}, itemCount={}, subtotal={}, taxAmount={},"
                            + " priorityCharge={}, totalAmount={}, reservationExpiresAt={}",
                    savedOrder.getId(),
                    savedOrder.getOrderNumber(),
                    previousPickupSlotId,
                    savedOrder.getPickupSlot().getId(),
                    previousPickupType,
                    savedOrder.getPickupType(),
                    savedOrder.getItems().size(),
                    savedOrder.getSubtotal(),
                    savedOrder.getTaxAmount(),
                    savedOrder.getPriorityCharge(),
                    savedOrder.getTotalAmount(),
                    savedOrder.getReservationExpiresAt());
            return toResponse(savedOrder);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderService.class,
                    "updatePendingCheckout(String,UpdatePendingOrderRequest)");
        }
    }

    // =========================================================
    // VALIDATE PENDING ORDER UPDATE
    // =========================================================
    /**
     * Validates pending checkout can be updated.
     *
     * @param order the order
     */
    private void validatePendingCheckoutCanBeUpdated(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderService.class, "validatePendingCheckoutCanBeUpdated(Order)");
        try {
            if (order.getOrderStatus() != OrderStatus.PENDING_PAYMENT) {
                log.warn(
                        "Pending checkout update rejected because order status is not"
                                + " PENDING_PAYMENT: orderId={}, orderNumber={}, status={}",
                        order.getId(),
                        order.getOrderNumber(),
                        order.getOrderStatus());
                throw new IllegalStateException("This order can no longer be changed.");
            }
            LocalDateTime reservationExpiresAt = order.getReservationExpiresAt();
            if (reservationExpiresAt == null) {
                log.error(
                        "Pending checkout update rejected because reservation expiry is missing:"
                                + " orderId={}, orderNumber={}",
                        order.getId(),
                        order.getOrderNumber());
                throw new IllegalStateException("Pickup reservation information is unavailable.");
            }
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            if (!reservationExpiresAt.isAfter(now)) {
                log.warn(
                        "Pending checkout update rejected because reservation expired: orderId={},"
                                + " orderNumber={}, reservationExpiresAt={}",
                        order.getId(),
                        order.getOrderNumber(),
                        reservationExpiresAt);
                throw new IllegalStateException(
                        "Your pickup reservation has expired. Please choose a pickup slot again.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderService.class,
                    "validatePendingCheckoutCanBeUpdated(Order)");
        }
    }

    // =========================================================
    // CLEAR REBATE
    // =========================================================
    /**
     * Clears applied rebate.
     *
     * @param order the order
     */
    private void clearAppliedRebate(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderService.class, "clearAppliedRebate(Order)");
        try {
            order.setRebateManualSelection(false);
            if (order.getRebate() == null
                    && order.getRebateCode() == null
                    && (order.getRebateDiscountAmount() == null
                            || order.getRebateDiscountAmount().compareTo(BigDecimal.ZERO) == 0)) {
                return;
            }
            log.info(
                    "Clearing applied rebate because pending order items changed: orderId={},"
                            + " orderNumber={}, rebateCode={}",
                    order.getId(),
                    order.getOrderNumber(),
                    order.getRebateCode());
            order.setRebate(null);
            order.setRebateCode(null);
            order.setRebateDiscountAmount(ZERO_MONEY);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OrderService.class, "clearAppliedRebate(Order)");
        }
    }

    // =========================================================
    // RESERVE PICKUP
    // =========================================================
    /**
     * Reserves pickup capacity.
     *
     * @param validatedOrder the validated order
     */
    private void reservePickupCapacity(ValidatedOrderData validatedOrder) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderService.class, "reservePickupCapacity(ValidatedOrderData)");
        try {
            Long slotId = validatedOrder.pickupSlot().getId();
            PickupType pickupType = validatedOrder.pickupType();
            switch (pickupType) {
                case NORMAL -> pickupSlotReservationService.reserveNormalCapacity(slotId);
                case PRIORITY -> pickupSlotReservationService.reservePriorityCapacity(slotId);
                case ADMIN_OVERRIDE ->
                        throw new IllegalArgumentException(
                                "Admin override cannot be used for a customer order.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderService.class,
                    "reservePickupCapacity(ValidatedOrderData)");
        }
    }

    // =========================================================
    // RELEASE PICKUP
    // =========================================================
    /**
     * Releases pickup capacity.
     *
     * @param slotId the slot id
     * @param pickupType the pickup type
     */
    private void releasePickupCapacity(Long slotId, PickupType pickupType) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderService.class, "releasePickupCapacity(Long,PickupType)");
        try {
            switch (pickupType) {
                case NORMAL -> pickupSlotReservationService.releaseNormalCapacity(slotId);
                case PRIORITY -> pickupSlotReservationService.releasePriorityCapacity(slotId);
                case ADMIN_OVERRIDE ->
                        throw new IllegalStateException(
                                "Admin override reservations cannot be changed through customer"
                                        + " checkout.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderService.class,
                    "releasePickupCapacity(Long,PickupType)");
        }
    }

    // =========================================================
    // BUILD ORDER
    // =========================================================
    /**
     * Builds order.
     *
     * @param request the request
     * @param validatedOrder the validated order
     * @param calculation the calculation
     * @return the build order result
     */
    private Order buildOrder(
            CreateOrderRequest request,
            ValidatedOrderData validatedOrder,
            OrderCalculationResult calculation) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderService.class,
                        "buildOrder(CreateOrderRequest,ValidatedOrderData,OrderCalculationResult)");
        try {
            Order order = new Order();
            order.setOrderNumber(orderNumberGenerator.generate());
            order.setBranch(validatedOrder.branch());
            order.setPickupSlot(validatedOrder.pickupSlot());
            order.setCustomerName(request.customerName().trim());
            order.setCustomerPhone(request.customerPhone());
            String normalizedPhone =
                    customerContactService.normalizeIndianMobile(request.customerPhone());
            order.setCustomerPhoneNormalized(normalizedPhone);
            CustomerContact customerContact =
                    customerContactService.resolveGuestContact(
                            normalizedPhone, request.customerName());
            order.setCustomerContact(customerContact);
            order.setPickupType(validatedOrder.pickupType());
            order.setPaymentFee(calculation.paymentFee());
            order.setPaymentFeeTax(calculation.paymentFeeTax());
            order.setPaymentFeeRate(calculation.paymentFeeRate());
            order.setPaymentFeeTaxRate(calculation.paymentFeeTaxRate());
            order.setConvenienceFee(calculation.convenienceFee());
            order.setConvenienceFeeTax(calculation.convenienceFeeTax());
            order.setConvenienceFeeTaxRate(calculation.convenienceFeeTaxRate());
            order.setPriorityCharge(calculation.priorityCharge());
            order.setSubtotal(calculation.subtotal());
            order.setTaxAmount(calculation.taxAmount());
            order.setTotalAmount(calculation.totalAmount());
            order.setOrderStatus(OrderStatus.PENDING_PAYMENT);
            LocalDateTime reservationExpiresAt =
                    LocalDateTime.now(BUSINESS_ZONE).plusMinutes(reservationExpiryMinutes);
            order.setReservationExpiresAt(reservationExpiresAt);
            order.setAdminOverride(false);
            order.setOverrideReason(null);
            return order;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderService.class,
                    "buildOrder(CreateOrderRequest,ValidatedOrderData,OrderCalculationResult)");
        }
    }

    // =========================================================
    // ADD ORDER ITEMS
    // =========================================================
    /**
     * Adds order items.
     *
     * @param order the order
     * @param calculation the calculation
     */
    private void addOrderItems(Order order, OrderCalculationResult calculation) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderService.class, "addOrderItems(Order,OrderCalculationResult)");
        try {
            for (CalculatedOrderItem calculatedItem : calculation.items()) {
                OrderItem item = new OrderItem();
                item.setProduct(calculatedItem.product());
                item.setProductName(calculatedItem.product().getName());
                item.setQuantity(calculatedItem.quantity());
                item.setSaleMode(calculatedItem.saleMode());
                item.setWeightGrams(calculatedItem.weightGrams());
                item.setUnitPrice(calculatedItem.unitPrice());
                item.setTaxRate(calculatedItem.taxRate());
                item.setTaxAmount(calculatedItem.taxAmount());
                item.setLineTotal(calculatedItem.lineTotal());
                order.addItem(item);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderService.class,
                    "addOrderItems(Order,OrderCalculationResult)");
        }
    }

    // =========================================================
    // RESPONSE
    // =========================================================
    /**
     * Tos response.
     *
     * @param order the order
     * @return the to response result
     */
    private OrderResponse toResponse(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderService.class, "toResponse(Order)");
        try {
            List<OrderItemResponse> itemResponses =
                    order.getItems().stream().map(this::toItemResponse).toList();
            return new OrderResponse(
                    order.getId(),
                    order.getOrderNumber(),
                    order.getBranch().getId(),
                    order.getPickupSlot().getId(),
                    order.getPickupSlot().getSlotDate(),
                    order.getPickupSlot().getStartTime(),
                    order.getPickupSlot().getEndTime(),
                    order.getCustomerName(),
                    order.getCustomerPhone(),
                    order.getPickupType(),
                    order.getPriorityCharge(),
                    order.getConvenienceFee(),
                    order.getConvenienceFeeTax(),
                    order.getPaymentFee(),
                    order.getPaymentFeeTax(),
                    order.getPaymentFeeRate(),
                    order.getSubtotal(),
                    order.getTaxAmount(),
                    order.getTotalAmount(),
                    order.getOrderStatus(),
                    order.isAdminOverride(),
                    order.getOverrideReason(),
                    itemResponses,
                    order.getReservationExpiresAt(),
                    order.getCreatedAt(),
                    order.getUpdatedAt());
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, OrderService.class, "toResponse(Order)");
        }
    }

    /**
     * Tos item response.
     *
     * @param item the item
     * @return the to item response result
     */
    private OrderItemResponse toItemResponse(OrderItem item) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderService.class, "toItemResponse(OrderItem)");
        try {
            return new OrderItemResponse(
                    item.getId(),
                    item.getProduct().getId(),
                    item.getProductName(),
                    item.getSaleMode(),
                    item.getQuantity(),
                    item.getWeightGrams(),
                    item.getUnitPrice(),
                    item.getTaxRate(),
                    item.getTaxAmount(),
                    item.getLineTotal());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OrderService.class, "toItemResponse(OrderItem)");
        }
    }
}
