package com.gokulsweets.restaurant.kot.service;

import com.gokulsweets.restaurant.kot.dto.AdminKotItemResponse;
import com.gokulsweets.restaurant.kot.dto.AdminKotResponse;
import com.gokulsweets.restaurant.kot.entity.Kot;
import com.gokulsweets.restaurant.kot.entity.KotItem;
import com.gokulsweets.restaurant.kot.repository.KotRepository;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.entity.OrderItem;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class KotService {

    private final KotRepository kotRepository;

    private final KotNumberGenerator kotNumberGenerator;

    private final StaffAuthorizationService
            staffAuthorizationService;

    private final PrintJobService
            printJobService;


    /*
     * =========================================================
     * CREATE / GET INTERNAL KOT
     * =========================================================
     */

    @Transactional
    public Kot getOrCreateForPreparation(
            Order order
    ) {

        validateOrder(
                order
        );


        Kot kot =
                kotRepository
                        .findByOrderId(
                                order.getId()
                        )
                        .orElseGet(
                                () ->
                                        createKot(
                                                order
                                        )
                        );


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
        printJobService
                .ensureInitialKotPrintJob(
                        kot
                );


        return kot;
    }


    /*
     * =========================================================
     * ADMIN: GET KOT BY KOT NUMBER
     * =========================================================
     */

    @Transactional(readOnly = true)
    public AdminKotResponse getAdminKotByNumber(
            String kotNumber
    ) {

        staffAuthorizationService
                .requirePermission(
                        PermissionName.ORDER_VIEW
                );


        String normalizedKotNumber =
                normalizeKotNumber(
                        kotNumber
                );


        Kot kot =
                kotRepository
                        .findDetailedByKotNumber(
                                normalizedKotNumber
                        )
                        .orElseThrow(() -> {

                            log.warn(
                                    "KOT not found: kotNumber={}",
                                    normalizedKotNumber
                            );

                            return new IllegalArgumentException(
                                    "KOT does not exist."
                            );
                        });


        staffAuthorizationService
                .requireBranchAccess(
                        kot.getBranch()
                                .getId()
                );


        return toAdminResponse(
                kot
        );
    }


    /*
     * =========================================================
     * ADMIN: GET KOT BY ORDER NUMBER
     * =========================================================
     */

    @Transactional(readOnly = true)
    public AdminKotResponse getAdminKotByOrderNumber(
            String orderNumber
    ) {

        staffAuthorizationService
                .requirePermission(
                        PermissionName.ORDER_VIEW
                );


        String normalizedOrderNumber =
                normalizeOrderNumber(
                        orderNumber
                );


        Kot kot =
                kotRepository
                        .findDetailedByOrderNumber(
                                normalizedOrderNumber
                        )
                        .orElseThrow(() -> {

                            log.warn(
                                    "KOT not found for order: orderNumber={}",
                                    normalizedOrderNumber
                            );

                            return new IllegalArgumentException(
                                    "A KOT does not exist for this order."
                            );
                        });


        staffAuthorizationService
                .requireBranchAccess(
                        kot.getBranch()
                                .getId()
                );


        return toAdminResponse(
                kot
        );
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

    @Transactional
    public AdminKotResponse recordPrintAttempt(
            String kotNumber
    ) {

        staffAuthorizationService
                .requirePermission(
                        PermissionName.ORDER_VIEW
                );


        String normalizedKotNumber =
                normalizeKotNumber(
                        kotNumber
                );


        Kot kot =
                kotRepository
                        .findForPrintByKotNumber(
                                normalizedKotNumber
                        )
                        .orElseThrow(() -> {

                            log.warn(
                                    "KOT print requested but KOT does not exist: kotNumber={}",
                                    normalizedKotNumber
                            );

                            return new IllegalArgumentException(
                                    "KOT does not exist."
                            );
                        });


        staffAuthorizationService
                .requireBranchAccess(
                        kot.getBranch()
                                .getId()
                );


        StaffUser currentStaff =
                staffAuthorizationService
                        .getCurrentStaff();


        String staffName =
                resolveStaffName(
                        currentStaff
                );


        LocalDateTime now =
                LocalDateTime.now();


        int currentPrintCount =
                kot.getPrintCount()
                        == null
                        ? 0
                        : kot.getPrintCount();


        /*
         * First-print audit is immutable.
         *
         * Once recorded, subsequent reprints must never
         * overwrite who initiated the first print.
         */
        if (
                currentPrintCount
                        ==
                        0
        ) {

            kot.setFirstPrintedAt(
                    now
            );


            kot.setFirstPrintedByStaffId(
                    currentStaff.getId()
            );


            kot.setFirstPrintedByStaffName(
                    staffName
            );
        }


        /*
         * Last-print audit always represents the most recent
         * print initiation.
         */
        kot.setLastPrintedAt(
                now
        );


        kot.setLastPrintedByStaffId(
                currentStaff.getId()
        );


        kot.setLastPrintedByStaffName(
                staffName
        );


        kot.setPrintCount(
                currentPrintCount
                        +
                        1
        );


        Kot savedKot =
                kotRepository
                        .saveAndFlush(
                                kot
                        );


        log.info(
                "KOT print initiated: kotId={}, kotNumber={}, orderNumber={}, branchId={}, printCount={}, printedByStaffId={}",
                savedKot.getId(),
                savedKot.getKotNumber(),
                savedKot.getOrder()
                        .getOrderNumber(),
                savedKot.getBranch()
                        .getId(),
                savedKot.getPrintCount(),
                currentStaff.getId()
        );


        return toAdminResponse(
                savedKot
        );
    }


    /*
     * =========================================================
     * CREATE KOT
     * =========================================================
     */

    private Kot createKot(
            Order order
    ) {

        StaffUser currentStaff =
                staffAuthorizationService
                        .getCurrentStaff();


        String staffName =
                resolveStaffName(
                        currentStaff
                );


        String kotNumber =
                kotNumberGenerator
                        .generate(
                                order.getId()
                        );


        log.info(
                "Creating KOT: kotNumber={}, orderId={}, orderNumber={}, branchId={}, startedByStaffId={}",
                kotNumber,
                order.getId(),
                order.getOrderNumber(),
                order.getBranch().getId(),
                currentStaff.getId()
        );


        Kot kot =
                new Kot();


        kot.setKotNumber(
                kotNumber
        );


        kot.setOrder(
                order
        );


        kot.setBranch(
                order.getBranch()
        );


        kot.setStartedByStaffId(
                currentStaff.getId()
        );


        kot.setStartedByStaffName(
                staffName
        );


        addItemSnapshots(
                kot,
                order.getItems()
        );


        Kot savedKot =
                kotRepository
                        .saveAndFlush(
                                kot
                        );


        log.info(
                "KOT created successfully: kotId={}, kotNumber={}, orderId={}, orderNumber={}, itemCount={}, startedByStaffId={}",
                savedKot.getId(),
                savedKot.getKotNumber(),
                order.getId(),
                order.getOrderNumber(),
                savedKot.getItems().size(),
                savedKot.getStartedByStaffId()
        );


        return savedKot;
    }


    /*
     * =========================================================
     * DTO MAPPING
     * =========================================================
     */

    private AdminKotResponse toAdminResponse(
            Kot kot
    ) {

        Order order =
                kot.getOrder();


        PickupSlot pickupSlot =
                order.getPickupSlot();


        if (pickupSlot == null) {

            throw new IllegalStateException(
                    "Pickup slot is unavailable for this KOT."
            );
        }


        List<AdminKotItemResponse> items =
                kot.getItems()
                        .stream()
                        .map(
                                item ->
                                        new AdminKotItemResponse(
                                                item.getId(),
                                                item.getProduct()
                                                        .getId(),
                                                item.getProductName(),
                                                item.getQuantity(),
                                                item.getDisplayOrder()
                                        )
                        )
                        .toList();


        return new AdminKotResponse(
                kot.getId(),
                kot.getKotNumber(),
                order.getOrderNumber(),

                kot.getBranch()
                        .getId(),

                kot.getBranch()
                        .getName(),

                kot.getBranch()
                        .getAddress(),

                pickupSlot.getSlotDate(),
                pickupSlot.getStartTime(),
                pickupSlot.getEndTime(),

                order.getPickupType(),

                kot.getStartedByStaffId(),
                kot.getStartedByStaffName(),

                kot.getCreatedAt(),
                kot.getFirstPrintedAt(),
                kot.getLastPrintedAt(),
                kot.getPrintCount(),

                items
        );
    }


    /*
     * =========================================================
     * ITEM SNAPSHOTS
     * =========================================================
     */

    private void addItemSnapshots(
            Kot kot,
            List<OrderItem> orderItems
    ) {

        if (
                orderItems == null
                        ||
                        orderItems.isEmpty()
        ) {

            throw new IllegalStateException(
                    "A KOT cannot be created for an order without items."
            );
        }


        int displayOrder =
                0;


        for (
                OrderItem orderItem :
                orderItems
        ) {

            KotItem kotItem =
                    new KotItem();


            kotItem.setProduct(
                    orderItem.getProduct()
            );


            kotItem.setProductName(
                    orderItem.getProductName()
            );


            kotItem.setQuantity(
                    orderItem.getQuantity()
            );


            kotItem.setDisplayOrder(
                    displayOrder
            );


            kot.addItem(
                    kotItem
            );


            displayOrder++;
        }
    }


    /*
     * =========================================================
     * VALIDATION
     * =========================================================
     */

    private void validateOrder(
            Order order
    ) {

        if (order == null) {

            throw new IllegalArgumentException(
                    "Order is required."
            );
        }


        if (order.getId() == null) {

            throw new IllegalStateException(
                    "The order must be persisted before a KOT can be created."
            );
        }


        if (order.getBranch() == null) {

            throw new IllegalStateException(
                    "The order branch is unavailable."
            );
        }


        if (
                order.getOrderStatus()
                        !=
                        OrderStatus.PREPARING
        ) {

            log.warn(
                    "KOT creation rejected because order is not PREPARING: orderId={}, orderNumber={}, status={}",
                    order.getId(),
                    order.getOrderNumber(),
                    order.getOrderStatus()
            );


            throw new IllegalStateException(
                    "A KOT can only be created when the order is preparing."
            );
        }
    }


    private String normalizeKotNumber(
            String kotNumber
    ) {

        if (
                kotNumber == null
                        ||
                        kotNumber.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "KOT number is required."
            );
        }


        return kotNumber.trim();
    }


    private String normalizeOrderNumber(
            String orderNumber
    ) {

        if (
                orderNumber == null
                        ||
                        orderNumber.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Order number is required."
            );
        }


        return orderNumber.trim();
    }


    /*
     * =========================================================
     * STAFF SNAPSHOT
     * =========================================================
     */

    private String resolveStaffName(
            StaffUser staff
    ) {

        if (
                staff.getFullName() != null
                        &&
                        !staff.getFullName()
                                .isBlank()
        ) {

            return staff
                    .getFullName()
                    .trim();
        }


        if (
                staff.getUsername() != null
                        &&
                        !staff.getUsername()
                                .isBlank()
        ) {

            return staff
                    .getUsername()
                    .trim();
        }


        throw new IllegalStateException(
                "The current staff user's display name is unavailable."
        );
    }
}