package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.branchproduct.BranchProductRepository;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.dto.CreateOrderRequest;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderData;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderItem;
import com.gokulsweets.restaurant.pickup.PickupSlot;
import com.gokulsweets.restaurant.pickup.PickupSlotValidationService;
import com.gokulsweets.restaurant.pickup.repository.PickupSlotRepository;
import com.gokulsweets.restaurant.product.Product;
import com.gokulsweets.restaurant.product.ProductSaleMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderValidationService {

    private final BranchRepository branchRepository;

    private final BranchProductRepository branchProductRepository;

    private final PickupSlotRepository pickupSlotRepository;

    private final PickupSlotValidationService pickupSlotValidationService;
    private final SmartOrderingRules smartOrderingRules;

    @Transactional(readOnly = true)
    public List<ValidatedOrderItem> validateCart(Long branchId, List<CreateOrderItemRequest> items) {
        validateBranch(branchId);
        return validateProducts(branchId, normalizeItems(items));
    }

    // =========================================================
    // VALIDATE NEW ORDER
    // =========================================================

    @Transactional(readOnly = true)
    public ValidatedOrderData validate(
            CreateOrderRequest request
    ) {

        log.debug(
                "Validating order request: branchId={}, pickupSlotId={}, pickupType={}, itemCount={}",
                request.branchId(),
                request.pickupSlotId(),
                request.pickupType(),
                request.items().size()
        );

        Branch branch =
                validateBranch(
                        request.branchId()
                );

        PickupSlot pickupSlot =
                validatePickupSlot(
                        request.pickupSlotId(),
                        branch.getId()
                );
        smartOrderingRules.validateWindow(pickupSlot);

        /*
         * New orders must validate that their requested
         * pickup mode can still take another reservation.
         */
        validatePickupTypeForNewReservation(
                request.pickupType(),
                pickupSlot
        );

        Map<Long, RequestedOrderItem> normalizedItems =
                normalizeItems(
                        request.items()
                );

        List<ValidatedOrderItem> items =
                validateProducts(
                        branch.getId(),
                        normalizedItems
                );

        log.debug(
                "Order validation completed successfully: branchId={}, pickupSlotId={}, normalizedItemCount={}",
                branch.getId(),
                pickupSlot.getId(),
                items.size()
        );

        return new ValidatedOrderData(
                branch,
                pickupSlot,
                request.pickupType(),
                items
        );
    }

    // =========================================================
    // VALIDATE EXISTING RESERVED ORDER UPDATE
    // =========================================================

    /*
     * This path is deliberately different from validating
     * a brand-new order.
     *
     * The order ALREADY owns one pickup reservation.
     *
     * Keeping the same slot and type does not require free
     * capacity because this order already owns it. Selecting
     * another slot or pickup type is validated exactly like a
     * new reservation before OrderService transfers capacity.
     *
     * We still validate:
     *
     * - branch remains active
     * - pickup slot remains active
     * - pickup window has not passed
     * - products remain available at that branch
     * - current backend prices/taxes
     */
    @Transactional(readOnly = true)
    public ValidatedOrderData validateExistingReservationUpdate(
            Order order,
            Long requestedPickupSlotId,
            PickupType requestedPickupType,
            List<CreateOrderItemRequest> requestedItems
    ) {

        if (order == null) {

            throw new IllegalArgumentException(
                    "Order is required."
            );
        }

        if (
                requestedPickupSlotId == null
        ) {

            throw new IllegalArgumentException(
                    "Pickup slot ID is required."
            );
        }

        if (
                requestedPickupType == null
        ) {

            throw new IllegalArgumentException(
                    "Pickup type is required."
            );
        }

        if (
                requestedItems == null
                        ||
                        requestedItems.isEmpty()
        ) {

            throw new IllegalArgumentException(
                    "At least one order item is required."
            );
        }

        Branch branch =
                order.getBranch();

        PickupSlot existingPickupSlot =
                order.getPickupSlot();

        if (
                branch == null
                        ||
                        existingPickupSlot == null
        ) {

            log.error(
                    "Existing order is missing branch or pickup slot: orderId={}",
                    order.getId()
            );

            throw new IllegalStateException(
                    "Order pickup information is incomplete."
            );
        }

        log.debug(
                "Validating pending order update: orderId={}, orderNumber={}, branchId={}, existingPickupSlotId={}, requestedPickupSlotId={}, requestedPickupType={}, itemCount={}",
                order.getId(),
                order.getOrderNumber(),
                branch.getId(),
                existingPickupSlot.getId(),
                requestedPickupSlotId,
                requestedPickupType,
                requestedItems.size()
        );

        /*
         * Existing reservation must still belong
         * to an active branch.
         */
        if (!branch.isActive()) {

            log.warn(
                    "Pending order update rejected because branch is inactive: orderId={}, branchId={}",
                    order.getId(),
                    branch.getId()
            );

            throw new IllegalStateException(
                    "Selected branch is currently unavailable."
            );
        }

        if (
                order.getPickupType()
                        ==
                        PickupType.ADMIN_OVERRIDE
        ) {

            log.warn(
                    "Customer pending-order update attempted on ADMIN_OVERRIDE order: orderId={}",
                    order.getId()
            );

            throw new IllegalStateException(
                    "This order cannot be changed through customer checkout."
            );
        }

        boolean keepsExistingReservation =
                existingPickupSlot
                        .getId()
                        .equals(
                                requestedPickupSlotId
                        )
                        &&
                        order.getPickupType()
                                ==
                                requestedPickupType;

        PickupSlot requestedPickupSlot;

        if (keepsExistingReservation) {

            /*
             * This order already owns this capacity, so a
             * full slot remains valid for this order.
             */
            validateOwnedPickupReservation(
                    order,
                    branch,
                    existingPickupSlot
            );

            requestedPickupSlot =
                    existingPickupSlot;

        } else {

            requestedPickupSlot =
                    validatePickupSlot(
                            requestedPickupSlotId,
                            branch.getId()
                    );
            smartOrderingRules.validateWindow(requestedPickupSlot);

            validatePickupTypeForNewReservation(
                    requestedPickupType,
                    requestedPickupSlot
            );
        }

        Map<Long, RequestedOrderItem> normalizedItems =
                normalizeItems(
                        requestedItems
                );

        List<ValidatedOrderItem> items =
                validateProducts(
                        branch.getId(),
                        normalizedItems
                );

        log.debug(
                "Pending order update validation completed: orderId={}, normalizedItemCount={}",
                order.getId(),
                items.size()
        );

        return new ValidatedOrderData(
                branch,
                requestedPickupSlot,
                requestedPickupType,
                items
        );
    }

    // =========================================================
    // VALIDATE CAPACITY ALREADY OWNED BY THIS ORDER
    // =========================================================

    private void validateOwnedPickupReservation(
            Order order,
            Branch branch,
            PickupSlot pickupSlot
    ) {

        if (!pickupSlot.isActive()) {

            throw new IllegalStateException(
                    "Your selected pickup slot is no longer available."
            );
        }

        if (
                !pickupSlot.getBranch()
                        .getId()
                        .equals(
                                branch.getId()
                        )
        ) {

            log.error(
                    "Pending order has pickup slot branch mismatch: orderId={}, branchId={}, pickupSlotId={}, slotBranchId={}",
                    order.getId(),
                    branch.getId(),
                    pickupSlot.getId(),
                    pickupSlot.getBranch().getId()
            );

            throw new IllegalStateException(
                    "Order pickup configuration is invalid."
            );
        }

        pickupSlotValidationService
                .validateNotPassed(
                        pickupSlot
                );

        if (
                order.getPickupType()
                        ==
                        PickupType.PRIORITY
                        &&
                        !pickupSlot.isPriorityEnabled()
        ) {

            throw new IllegalStateException(
                    "Priority pickup is no longer available for this slot."
            );
        }
    }

    // =========================================================
    // VALIDATE BRANCH
    // =========================================================

    private Branch validateBranch(
            Long branchId
    ) {

        Branch branch =
                branchRepository
                        .findById(
                                branchId
                        )
                        .orElseThrow(() -> {

                            log.warn(
                                    "Order validation failed because branch does not exist: branchId={}",
                                    branchId
                            );

                            return new IllegalArgumentException(
                                    "Selected branch does not exist."
                            );
                        });

        if (!branch.isActive()) {

            log.warn(
                    "Order validation failed because branch is inactive: branchId={}",
                    branchId
            );

            throw new IllegalArgumentException(
                    "Selected branch is currently unavailable."
            );
        }

        return branch;
    }

    // =========================================================
    // VALIDATE PICKUP SLOT
    // =========================================================

    private PickupSlot validatePickupSlot(
            Long pickupSlotId,
            Long branchId
    ) {

        PickupSlot slot =
                pickupSlotRepository
                        .findById(
                                pickupSlotId
                        )
                        .orElseThrow(() -> {

                            log.warn(
                                    "Order validation failed because pickup slot does not exist: pickupSlotId={}",
                                    pickupSlotId
                            );

                            return new IllegalArgumentException(
                                    "Selected pickup slot does not exist."
                            );
                        });

        if (
                !slot.getBranch()
                        .getId()
                        .equals(
                                branchId
                        )
        ) {

            log.warn(
                    "Pickup slot branch mismatch: pickupSlotId={}, slotBranchId={}, requestedBranchId={}",
                    pickupSlotId,
                    slot.getBranch().getId(),
                    branchId
            );

            throw new IllegalArgumentException(
                    "Selected pickup slot does not belong to the selected branch."
            );
        }

        if (!slot.isActive()) {

            log.warn(
                    "Order validation failed because pickup slot is inactive: pickupSlotId={}",
                    pickupSlotId
            );

            throw new IllegalArgumentException(
                    "Selected pickup slot is no longer available."
            );
        }

        pickupSlotValidationService
                .validateNotPassed(
                        slot
                );

        return slot;
    }

    // =========================================================
    // VALIDATE NEW PICKUP RESERVATION
    // =========================================================

    private void validatePickupTypeForNewReservation(
            PickupType pickupType,
            PickupSlot slot
    ) {

        if (
                pickupType
                        ==
                        PickupType.ADMIN_OVERRIDE
        ) {

            log.warn(
                    "ADMIN_OVERRIDE attempted through public order flow: pickupSlotId={}",
                    slot.getId()
            );

            throw new IllegalArgumentException(
                    "Admin override cannot be used for a customer order."
            );
        }

        if (
                pickupType
                        ==
                        PickupType.PRIORITY
        ) {

            if (!slot.isPriorityEnabled()) {

                log.warn(
                        "Priority pickup requested for slot without priority enabled: pickupSlotId={}",
                        slot.getId()
                );

                throw new IllegalArgumentException(
                        "Priority pickup is not available for the selected slot."
                );
            }

            if (
                    slot.getPriorityBookedCount()
                            >=
                            slot.getPriorityCapacity()
            ) {

                log.warn(
                        "Priority pickup capacity exhausted: pickupSlotId={}",
                        slot.getId()
                );

                throw new IllegalArgumentException(
                        "Priority pickup is no longer available for the selected slot."
                );
            }
        }
    }

    // =========================================================
    // NORMALIZE ITEMS
    // =========================================================

    private Map<Long, RequestedOrderItem> normalizeItems(
            List<CreateOrderItemRequest> items
    ) {

        Map<Long, RequestedOrderItem> normalized =
                new LinkedHashMap<>();

        for (
                CreateOrderItemRequest item :
                items
        ) {

            if (
                    item.productId()
                            ==
                            null
            ) {

                throw new IllegalArgumentException(
                        "Product ID is required."
                );
            }

            normalized.merge(
                    item.productId(),
                    new RequestedOrderItem(
                            item.quantity(),
                            item.weightGrams()
                    ),
                    (
                            existing,
                            additional
                    ) -> {

                        try {

                            return new RequestedOrderItem(
                                    addNullableExact(
                                            existing.quantity(),
                                            additional.quantity()
                                    ),
                                    addNullableExact(
                                            existing.weightGrams(),
                                            additional.weightGrams()
                                    )
                            );

                        } catch (
                                ArithmeticException exception
                        ) {

                            log.warn(
                                    "Order amount overflow while merging duplicate product: productId={}",
                                    item.productId()
                            );

                            throw new IllegalArgumentException(
                                    "Requested product amount is too large."
                            );
                        }
                    }
            );
        }

        return normalized;
    }

    // =========================================================
    // VALIDATE PRODUCTS
    // =========================================================

    private List<ValidatedOrderItem> validateProducts(
            Long branchId,
            Map<Long, RequestedOrderItem> requestedItems
    ) {

        Set<Long> productIds =
                requestedItems.keySet();

        List<BranchProduct> branchProducts =
                branchProductRepository
                        .findForOrder(
                                branchId,
                                productIds
                        );

        Map<Long, BranchProduct> branchProductMap =
                branchProducts
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        branchProduct ->
                                                branchProduct
                                                        .getProduct()
                                                        .getId(),

                                        Function.identity()
                                )
                        );

        List<ValidatedOrderItem> validated =
                new ArrayList<>();

        for (
                Map.Entry<Long, RequestedOrderItem> entry :
                requestedItems.entrySet()
        ) {

            Long productId =
                    entry.getKey();

            BranchProduct branchProduct =
                    branchProductMap.get(
                            productId
                    );

            if (
                    branchProduct == null
            ) {

                log.warn(
                        "Product is not configured for selected branch: branchId={}, productId={}",
                        branchId,
                        productId
                );

                throw new IllegalArgumentException(
                        "One or more selected products are not available at this branch."
                );
            }

            Product product =
                    branchProduct.getProduct();

            RequestedOrderItem requestedItem =
                    entry.getValue();

            if (!product.isActive()) {

                log.warn(
                        "Inactive product requested: branchId={}, productId={}",
                        branchId,
                        productId
                );

                throw new IllegalArgumentException(
                        "One or more selected products are currently unavailable."
                );
            }

            if (!branchProduct.isAvailable()) {

                log.warn(
                        "Branch product marked unavailable: branchId={}, productId={}",
                        branchId,
                        productId
                );

                throw new IllegalArgumentException(
                        "One or more selected products are currently unavailable at this branch."
                );
            }

            if (
                    product.getBasePrice()
                            ==
                            null
            ) {

                log.error(
                        "Product has no base price configured: productId={}",
                        productId
                );

                throw new IllegalStateException(
                        "Product pricing is not configured correctly."
                );
            }

            if (
                    product.getTaxCategory()
                            !=
                            null
                            &&
                            !product.getTaxCategory()
                                    .isActive()
            ) {

                log.error(
                        "Product is linked to inactive tax category: productId={}, taxCategoryId={}",
                        productId,
                        product.getTaxCategory()
                                .getId()
                );

                throw new IllegalStateException(
                        "Product tax configuration is not available."
                );
            }

            ProductSaleMode saleMode =
                    product.getSaleMode() == null
                            ? ProductSaleMode.UNIT
                            : product.getSaleMode();

            if (saleMode == ProductSaleMode.WEIGHT) {

                validateWeightRequest(
                        product,
                        requestedItem
                );

                validated.add(
                        new ValidatedOrderItem(
                                product,
                                branchProduct,
                                saleMode,
                                1,
                                requestedItem.weightGrams()
                        )
                );

            } else {

                validateUnitRequest(
                        product,
                        requestedItem
                );

                validated.add(
                        new ValidatedOrderItem(
                                product,
                                branchProduct,
                                saleMode,
                                requestedItem.quantity(),
                                null
                        )
                );
            }
        }

        return validated;
    }

    private void validateUnitRequest(
            Product product,
            RequestedOrderItem request
    ) {

        if (
                request.quantity() == null
                        || request.quantity() <= 0
        ) {
            throw new IllegalArgumentException(
                    product.getName() + " requires a quantity of at least 1."
            );
        }

        if (request.weightGrams() != null) {
            throw new IllegalArgumentException(
                    product.getName() + " is sold by quantity, not by weight."
            );
        }
    }

    private void validateWeightRequest(
            Product product,
            RequestedOrderItem request
    ) {

        Integer minimumWeight =
                product.getMinimumWeightGrams();

        Integer weightStep =
                product.getWeightStepGrams();

        if (
                minimumWeight == null
                        || minimumWeight < 250
                        || weightStep == null
                        || weightStep <= 0
        ) {
            log.error(
                    "Invalid weight configuration: productId={}, minimumWeightGrams={}, weightStepGrams={}",
                    product.getId(),
                    minimumWeight,
                    weightStep
            );

            throw new IllegalStateException(
                    "Product weight configuration is unavailable."
            );
        }

        Integer requestedWeight =
                request.weightGrams();

        if (requestedWeight == null) {
            throw new IllegalArgumentException(
                    "Please select a weight for " + product.getName() + "."
            );
        }

        if (requestedWeight < minimumWeight) {
            throw new IllegalArgumentException(
                    product.getName()
                            + " has a minimum order of "
                            + minimumWeight
                            + " grams."
            );
        }

        if ((requestedWeight - minimumWeight) % weightStep != 0) {
            throw new IllegalArgumentException(
                    product.getName()
                            + " must be ordered in "
                            + weightStep
                            + " gram steps starting from "
                            + minimumWeight
                            + " grams."
            );
        }

        if (request.quantity() != null && request.quantity() != 1) {
            throw new IllegalArgumentException(
                    product.getName() + " is sold by weight, not by quantity."
            );
        }
    }

    private Integer addNullableExact(
            Integer first,
            Integer second
    ) {

        if (first == null) {
            return second;
        }

        if (second == null) {
            return first;
        }

        return Math.addExact(first, second);
    }

    private record RequestedOrderItem(
            Integer quantity,
            Integer weightGrams
    ) {
    }
}
