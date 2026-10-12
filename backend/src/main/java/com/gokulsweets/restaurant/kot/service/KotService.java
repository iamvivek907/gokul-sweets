package com.gokulsweets.restaurant.kot.service;

import com.gokulsweets.restaurant.delivery.DeliveryOrderWindowLookup;
import com.gokulsweets.restaurant.kot.dto.AdminKotItemResponse;
import com.gokulsweets.restaurant.kot.dto.AdminKotResponse;
import com.gokulsweets.restaurant.kot.entity.Kot;
import com.gokulsweets.restaurant.kot.entity.KotItem;
import com.gokulsweets.restaurant.kot.repository.KotRepository;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.entity.OrderItem;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.pickup.PickupSlot;
import com.gokulsweets.restaurant.printing.service.PrintJobService;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.staff.StaffUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Coordinates kot operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class KotService {

    private final KotRepository kotRepository;

    private final KotNumberGenerator kotNumberGenerator;

    private final StaffAuthorizationService staffAuthorizationService;

    private final PrintJobService printJobService;

    private final DeliveryOrderWindowLookup deliveryWindows;

    /*
     * =========================================================
     * CREATE / GET INTERNAL KOT
     * =========================================================
     */
    /**
     * Returns or create for preparation.
     *
     * @param order the order
     * @return the get or create for preparation result
     */
    @Transactional
    public Kot getOrCreateForPreparation(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(KotService.class, "getOrCreateForPreparation(Order)");
        try {
            validateOrder(order);
            Kot kot = kotRepository.findByOrderId(order.getId()).orElseGet(() -> createKot(order));
            /*
             * Every KOT must have one durable INITIAL_KOT print job.
             *
             * This is intentionally executed for both:
             *
             * 1. a newly-created KOT
             * 2. an already-existing KOT
             *
             * That also repairs older PREPARING KOTs that may have
             * been created before automatic printing was introduced.
             *
             * Because this runs inside the same transaction as the
             * preparation workflow, newly-created KOT + print job
             * commit together.
             */
            printJobService.ensureInitialKotPrintJob(kot);
            return kot;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    KotService.class,
                    "getOrCreateForPreparation(Order)");
        }
    }

    /*
     * =========================================================
     * ADMIN: GET KOT BY KOT NUMBER
     * =========================================================
     */
    /**
     * Returns admin kot by number.
     *
     * @param kotNumber the kot number
     * @return the get admin kot by number result
     */
    @Transactional(readOnly = true)
    public AdminKotResponse getAdminKotByNumber(String kotNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(KotService.class, "getAdminKotByNumber(String)");
        try {
            staffAuthorizationService.requirePermission(PermissionName.ORDER_VIEW);
            String normalizedKotNumber = normalizeKotNumber(kotNumber);
            Kot kot =
                    kotRepository
                            .findDetailedByKotNumber(normalizedKotNumber)
                            .orElseThrow(
                                    () -> {
                                        log.warn(
                                                "KOT not found: kotNumber={}", normalizedKotNumber);
                                        return new IllegalArgumentException("KOT does not exist.");
                                    });
            staffAuthorizationService.requireBranchAccess(kot.getBranch().getId());
            return toAdminResponse(kot);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, KotService.class, "getAdminKotByNumber(String)");
        }
    }

    /*
     * =========================================================
     * ADMIN: GET KOT BY ORDER NUMBER
     * =========================================================
     */
    /**
     * Returns admin kot by order number.
     *
     * @param orderNumber the order number
     * @return the get admin kot by order number result
     */
    @Transactional(readOnly = true)
    public AdminKotResponse getAdminKotByOrderNumber(String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(KotService.class, "getAdminKotByOrderNumber(String)");
        try {
            staffAuthorizationService.requirePermission(PermissionName.ORDER_VIEW);
            String normalizedOrderNumber = normalizeOrderNumber(orderNumber);
            Kot kot =
                    kotRepository
                            .findDetailedByOrderNumber(normalizedOrderNumber)
                            .orElseThrow(
                                    () -> {
                                        log.warn(
                                                "KOT not found for order: orderNumber={}",
                                                normalizedOrderNumber);
                                        return new IllegalArgumentException(
                                                "A KOT does not exist for this order.");
                                    });
            staffAuthorizationService.requireBranchAccess(kot.getBranch().getId());
            return toAdminResponse(kot);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    KotService.class,
                    "getAdminKotByOrderNumber(String)");
        }
    }

    /*
     * =========================================================
     * ADMIN: RECORD PRINT ATTEMPT
     * =========================================================
     *
     * Stage-1 browser printing cannot reliably confirm that a
     * physical printer successfully produced paper.
     *
     * Therefore this records that an authorized staff member
     * initiated printing through the application.
     *
     * The KOT row is pessimistically locked so concurrent
     * terminals cannot lose print-count updates or overwrite
     * the true first-print identity.
     */
    /**
     * Records print attempt.
     *
     * @param kotNumber the kot number
     * @return the record print attempt result
     */
    @Transactional
    public AdminKotResponse recordPrintAttempt(String kotNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(KotService.class, "recordPrintAttempt(String)");
        try {
            staffAuthorizationService.requirePermission(PermissionName.ORDER_VIEW);
            String normalizedKotNumber = normalizeKotNumber(kotNumber);
            Kot kot =
                    kotRepository
                            .findForPrintByKotNumber(normalizedKotNumber)
                            .orElseThrow(
                                    () -> {
                                        log.warn(
                                                "KOT print requested but KOT does not exist:"
                                                        + " kotNumber={}",
                                                normalizedKotNumber);
                                        return new IllegalArgumentException("KOT does not exist.");
                                    });
            staffAuthorizationService.requireBranchAccess(kot.getBranch().getId());
            StaffUser currentStaff = staffAuthorizationService.getCurrentStaff();
            String staffName = resolveStaffName(currentStaff);
            LocalDateTime now = LocalDateTime.now();
            int currentPrintCount = kot.getPrintCount() == null ? 0 : kot.getPrintCount();
            /*
             * First-print audit is immutable.
             *
             * Once recorded, subsequent reprints must never
             * overwrite who initiated the first print.
             */
            if (currentPrintCount == 0) {
                kot.setFirstPrintedAt(now);
                kot.setFirstPrintedByStaffId(currentStaff.getId());
                kot.setFirstPrintedByStaffName(staffName);
            }
            /*
             * Last-print audit always represents the most recent
             * print initiation.
             */
            kot.setLastPrintedAt(now);
            kot.setLastPrintedByStaffId(currentStaff.getId());
            kot.setLastPrintedByStaffName(staffName);
            kot.setPrintCount(currentPrintCount + 1);
            Kot savedKot = kotRepository.saveAndFlush(kot);
            log.info(
                    "KOT print initiated: kotId={}, kotNumber={}, orderNumber={}, branchId={},"
                            + " printCount={}, printedByStaffId={}",
                    savedKot.getId(),
                    savedKot.getKotNumber(),
                    savedKot.getOrder().getOrderNumber(),
                    savedKot.getBranch().getId(),
                    savedKot.getPrintCount(),
                    currentStaff.getId());
            return toAdminResponse(savedKot);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, KotService.class, "recordPrintAttempt(String)");
        }
    }

    /*
     * =========================================================
     * CREATE KOT
     * =========================================================
     */
    /**
     * Creates kot.
     *
     * @param order the order
     * @return the create kot result
     */
    private Kot createKot(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(KotService.class, "createKot(Order)");
        try {
            StaffUser currentStaff = staffAuthorizationService.getCurrentStaff();
            String staffName = resolveStaffName(currentStaff);
            String kotNumber = kotNumberGenerator.generate(order.getId());
            log.info(
                    "Creating KOT: kotNumber={}, orderId={}, orderNumber={}, branchId={},"
                            + " startedByStaffId={}",
                    kotNumber,
                    order.getId(),
                    order.getOrderNumber(),
                    order.getBranch().getId(),
                    currentStaff.getId());
            Kot kot = new Kot();
            kot.setKotNumber(kotNumber);
            kot.setOrder(order);
            kot.setBranch(order.getBranch());
            kot.setStartedByStaffId(currentStaff.getId());
            kot.setStartedByStaffName(staffName);
            addItemSnapshots(kot, order.getItems());
            Kot savedKot = kotRepository.saveAndFlush(kot);
            log.info(
                    "KOT created successfully: kotId={}, kotNumber={}, orderId={}, orderNumber={},"
                            + " itemCount={}, startedByStaffId={}",
                    savedKot.getId(),
                    savedKot.getKotNumber(),
                    order.getId(),
                    order.getOrderNumber(),
                    savedKot.getItems().size(),
                    savedKot.getStartedByStaffId());
            return savedKot;
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, KotService.class, "createKot(Order)");
        }
    }

    /*
     * =========================================================
     * DTO MAPPING
     * =========================================================
     */
    /**
     * Tos admin response.
     *
     * @param kot the kot
     * @return the to admin response result
     */
    private AdminKotResponse toAdminResponse(Kot kot) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(KotService.class, "toAdminResponse(Kot)");
        try {
            Order order = kot.getOrder();
            PickupSlot pickupSlot = order.getPickupSlot();
            var deliveryWindow =
                    order.getFulfillmentType() == FulfillmentType.DELIVERY
                            ? deliveryWindows.require(order)
                            : null;
            if (pickupSlot == null && deliveryWindow == null) {
                throw new IllegalStateException("Pickup slot is unavailable for this KOT.");
            }
            List<AdminKotItemResponse> items =
                    kot.getItems().stream()
                            .map(
                                    item ->
                                            new AdminKotItemResponse(
                                                    item.getId(),
                                                    item.getProduct().getId(),
                                                    item.getProductName(),
                                                    item.getQuantity(),
                                                    item.getDisplayOrder(),
                                                    item.getSaleMode(),
                                                    item.getWeightGrams()))
                            .toList();
            return new AdminKotResponse(
                    kot.getId(),
                    kot.getKotNumber(),
                    order.getOrderNumber(),
                    order.getCustomerOrderNumber(),
                    kot.getBranch().getId(),
                    kot.getBranch().getName(),
                    kot.getBranch().getAddress(),
                    pickupSlot == null ? null : pickupSlot.getSlotDate(),
                    pickupSlot == null ? null : pickupSlot.getStartTime(),
                    pickupSlot == null ? null : pickupSlot.getEndTime(),
                    order.getPickupType(),
                    kot.getStartedByStaffId(),
                    kot.getStartedByStaffName(),
                    kot.getCreatedAt(),
                    kot.getFirstPrintedAt(),
                    kot.getLastPrintedAt(),
                    kot.getPrintCount(),
                    items,
                    order.getFulfillmentType(),
                    deliveryWindow == null ? null : deliveryWindow.date(),
                    deliveryWindow == null ? null : deliveryWindow.start(),
                    deliveryWindow == null ? null : deliveryWindow.end());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, KotService.class, "toAdminResponse(Kot)");
        }
    }

    /*
     * =========================================================
     * ITEM SNAPSHOTS
     * =========================================================
     */
    /**
     * Adds item snapshots.
     *
     * @param kot the kot
     * @param orderItems the order items
     */
    private void addItemSnapshots(Kot kot, List<OrderItem> orderItems) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(KotService.class, "addItemSnapshots(Kot,List<OrderItem>)");
        try {
            if (orderItems == null || orderItems.isEmpty()) {
                throw new IllegalStateException(
                        "A KOT cannot be created for an order without items.");
            }
            int displayOrder = 0;
            for (OrderItem orderItem : orderItems) {
                KotItem kotItem = new KotItem();
                kotItem.setProduct(orderItem.getProduct());
                kotItem.setProductName(orderItem.getProductName());
                kotItem.setQuantity(orderItem.getQuantity());
                kotItem.setSaleMode(orderItem.getSaleMode().name());
                kotItem.setWeightGrams(orderItem.getWeightGrams());
                kotItem.setDisplayOrder(displayOrder);
                kot.addItem(kotItem);
                displayOrder++;
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    KotService.class,
                    "addItemSnapshots(Kot,List<OrderItem>)");
        }
    }

    /*
     * =========================================================
     * VALIDATION
     * =========================================================
     */
    /**
     * Validates order.
     *
     * @param order the order
     */
    private void validateOrder(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(KotService.class, "validateOrder(Order)");
        try {
            if (order == null) {
                throw new IllegalArgumentException("Order is required.");
            }
            if (order.getId() == null) {
                throw new IllegalStateException(
                        "The order must be persisted before a KOT can be created.");
            }
            if (order.getBranch() == null) {
                throw new IllegalStateException("The order branch is unavailable.");
            }
            if (order.getOrderStatus() != OrderStatus.PREPARING) {
                log.warn(
                        "KOT creation rejected because order is not PREPARING: orderId={},"
                                + " orderNumber={}, status={}",
                        order.getId(),
                        order.getOrderNumber(),
                        order.getOrderStatus());
                throw new IllegalStateException(
                        "A KOT can only be created when the order is preparing.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, KotService.class, "validateOrder(Order)");
        }
    }

    /**
     * Normalizes kot number.
     *
     * @param kotNumber the kot number
     * @return the normalize kot number result
     */
    private String normalizeKotNumber(String kotNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(KotService.class, "normalizeKotNumber(String)");
        try {
            if (kotNumber == null || kotNumber.isBlank()) {
                throw new IllegalArgumentException("KOT number is required.");
            }
            return kotNumber.trim();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, KotService.class, "normalizeKotNumber(String)");
        }
    }

    /**
     * Normalizes order number.
     *
     * @param orderNumber the order number
     * @return the normalize order number result
     */
    private String normalizeOrderNumber(String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(KotService.class, "normalizeOrderNumber(String)");
        try {
            if (orderNumber == null || orderNumber.isBlank()) {
                throw new IllegalArgumentException("Order number is required.");
            }
            return orderNumber.trim();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, KotService.class, "normalizeOrderNumber(String)");
        }
    }

    /*
     * =========================================================
     * STAFF SNAPSHOT
     * =========================================================
     */
    /**
     * Resolves staff name.
     *
     * @param staff the staff
     * @return the resolve staff name result
     */
    private String resolveStaffName(StaffUser staff) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(KotService.class, "resolveStaffName(StaffUser)");
        try {
            if (staff.getFullName() != null && !staff.getFullName().isBlank()) {
                return staff.getFullName().trim();
            }
            if (staff.getUsername() != null && !staff.getUsername().isBlank()) {
                return staff.getUsername().trim();
            }
            throw new IllegalStateException(
                    "The current staff user's display name is unavailable.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, KotService.class, "resolveStaffName(StaffUser)");
        }
    }
}
