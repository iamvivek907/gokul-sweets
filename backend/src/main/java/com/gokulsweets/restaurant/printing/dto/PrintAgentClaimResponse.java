package com.gokulsweets.restaurant.printing.dto;

import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.printing.enums.PrinterProtocol;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Immutable print agent claim response data contract.
 *
 * @param printJobId the print job id
 * @param claimToken the claim token
 * @param leaseExpiresAt the lease expires at
 * @param copies the copies
 * @param printer the printer
 * @param kot the kot
 */
public record PrintAgentClaimResponse(
        Long printJobId,
        String claimToken,
        LocalDateTime leaseExpiresAt,
        Integer copies,
        Printer printer,
        KotPayload kot) {

    /**
     * Immutable printer data contract.
     *
     * @param id the id
     * @param code the code
     * @param name the name
     * @param station the station
     * @param protocol the protocol
     * @param host the host
     * @param port the port
     * @param paperWidthMm the paper width mm
     * @param autoCut the auto cut
     */
    public record Printer(
            Long id,
            String code,
            String name,
            PrinterStation station,
            PrinterProtocol protocol,
            String host,
            Integer port,
            Integer paperWidthMm,
            boolean autoCut) {}

    /**
     * Immutable kot payload data contract.
     *
     * @param kotId the kot id
     * @param kotNumber the kot number
     * @param orderNumber the order number
     * @param customerOrderNumber the customer order number
     * @param branchName the branch name
     * @param branchAddress the branch address
     * @param pickupDate the pickup date
     * @param pickupStartTime the pickup start time
     * @param pickupEndTime the pickup end time
     * @param pickupType the pickup type
     * @param startedByStaffName the started by staff name
     * @param createdAt the created at
     * @param items the items
     * @param fulfillmentType the fulfillment type
     * @param deliveryDate the delivery date
     * @param deliveryStartTime the delivery start time
     * @param deliveryEndTime the delivery end time
     */
    public record KotPayload(
            Long kotId,
            String kotNumber,
            String orderNumber,
            Long customerOrderNumber,
            String branchName,
            String branchAddress,
            LocalDate pickupDate,
            LocalTime pickupStartTime,
            LocalTime pickupEndTime,
            PickupType pickupType,
            String startedByStaffName,
            LocalDateTime createdAt,
            List<Item> items,
            com.gokulsweets.restaurant.order.enums.FulfillmentType fulfillmentType,
            LocalDate deliveryDate,
            LocalTime deliveryStartTime,
            LocalTime deliveryEndTime) {}

    /**
     * Immutable item data contract.
     *
     * @param productName the product name
     * @param quantity the quantity
     * @param displayOrder the display order
     * @param saleMode the sale mode
     * @param weightGrams the weight grams
     * @param quantityLabel the quantity label
     */
    public record Item(
            String productName,
            Integer quantity,
            Integer displayOrder,
            String saleMode,
            Integer weightGrams,
            String quantityLabel) {

        /**
         * Creates a item instance.
         *
         * @param productName the product name
         * @param quantity the quantity
         * @param displayOrder the display order
         */
        public Item(String productName, Integer quantity, Integer displayOrder) {
            this(productName, quantity, displayOrder, "UNIT", null, quantity + " pcs");
        }
    }
}
