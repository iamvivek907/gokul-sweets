package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.CustomerContactService;
import com.gokulsweets.restaurant.customer.identity.VerifiedOrderOwnership;
import com.gokulsweets.restaurant.inventory.service.OrderInventoryReservationService;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.entity.OrderItem;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.order.service.OrderIdempotencyService;
import com.gokulsweets.restaurant.order.service.OrderNumberGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;

/** Atomic delivery order transaction. Customer entry remains OFF until the delivery checkout flag is enabled. */
@Service
@RequiredArgsConstructor
public class DeliveryOrderCreationService {
    private final EnhancementProperties flags;
    private final DeliveryOrderPreparationService preparation;
    private final DeliveryAcceptedQuoteService acceptedQuotes;
    private final DeliveryRiderHoldService riders;
    private final OrderInventoryReservationService inventory;
    private final OrderIdempotencyService idempotency;
    private final OrderRepository orders;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final OrderNumberGenerator numbers;
    private final CustomerContactService contacts;
    private final VerifiedOrderOwnership ownership;
    private final Clock inventoryClock;

    @Transactional
    public Created create(CreateRequest request, String idempotencyKey, String identityToken) {
        if (!flags.deliveryCheckoutReady())
            throw new IllegalStateException("Delivery order creation is disabled.");
        validate(request);
        String fingerprint = fingerprint(request);
        var claim = idempotency.claim(idempotencyKey, fingerprint);
        if (!claim.newRequest()) {
            Order existing = claim.existingOrder();
            if (existing.getFulfillmentType() != FulfillmentType.DELIVERY)
                throw new IllegalStateException("This idempotency key belongs to a pickup order.");
            return response(existing);
        }

        var prepared = preparation.prepare(request.quote(), request.windowId());
        acceptedQuotes.accept(request, prepared.price(), prepared.economics());
        String holdKey = "delivery-" + digest(idempotencyKey);
        // The 14-minute checkout deadline fits inside the server's 15-minute rider hold.
        LocalDateTime expiresAt = LocalDateTime.now(inventoryClock).plusMinutes(14);
        if (!riders.hold(holdKey, fingerprint, request.quote(), request.windowId()))
            throw new IllegalStateException("The selected delivery window is no longer available.");

        var order = new Order();
        order.setOrderNumber(numbers.generate());
        order.setBranch(prepared.validated().branch());
        order.setFulfillmentType(FulfillmentType.DELIVERY);
        order.setDeliveryWindowId(request.windowId());
        order.setDeliveryHoldKey(holdKey);
        order.setDeliveryAddressLine(request.addressLine().trim());
        order.setDeliveryLocality(request.quote().locality().trim());
        order.setDeliveryPostalCode(request.quote().postalCode());
        order.setCustomerName(request.customerName().trim());
        order.setCustomerPhone(request.customerPhone());
        String normalizedPhone = contacts.normalizeIndianMobile(request.customerPhone());
        order.setCustomerPhoneNormalized(normalizedPhone);
        order.setCustomerContact(contacts.resolveGuestContact(normalizedPhone, request.customerName()));
        order.setSubtotal(prepared.price().subtotal());
        order.setTaxAmount(prepared.price().taxAmount());
        order.setPriorityCharge(prepared.price().priorityCharge());
        order.setDeliveryFee(prepared.economics().fee());
        order.setTotalAmount(prepared.price().totalAmount().add(prepared.economics().fee()));
        order.setOrderStatus(OrderStatus.PENDING_PAYMENT);
        order.setReservationExpiresAt(expiresAt);
        for (var calculated : prepared.price().items()) {
            var item = new OrderItem();
            item.setProduct(calculated.product());
            item.setProductName(calculated.product().getName());
            item.setQuantity(calculated.quantity());
            item.setSaleMode(calculated.saleMode());
            item.setWeightGrams(calculated.weightGrams());
            item.setUnitPrice(calculated.unitPrice());
            item.setTaxRate(calculated.taxRate());
            item.setTaxAmount(calculated.taxAmount());
            item.setLineTotal(calculated.lineTotal());
            order.addItem(item);
        }
        Order saved = orders.saveAndFlush(order);
        if (flags.isDeliveryEconomics()) {
            var e = prepared.economics();
            jdbc.update("""
                    INSERT INTO delivery_economics_snapshots (order_id, version, food_cost, packaging_cost,
                        labour_cost, waste_cost, payment_cost, journey_cost, remedy_cost, delivery_fee, contribution)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, saved.getId(), e.version(), e.foodCost(), e.packagingCost(), e.labourCost(),
                    e.wasteCost(), e.paymentCost(), e.journeyCost(), e.remedyCost(), e.fee(), e.contribution());
        }
        ownership.bindNewOrder(saved.getId(), request.customerPhone(), identityToken);
        inventory.synchronizePendingDeliveryOrder(saved, prepared.validated());
        idempotency.linkOrder(idempotencyKey, saved);
        return response(saved);
    }

    static void validate(CreateRequest request) {
        if (request == null || request.quote() == null || request.windowId() <= 0
                || request.quote().branchId() <= 0 || request.quote().serviceDate() == null
                || request.quote().locality() == null || request.quote().postalCode() == null
                || request.quote().locality().trim().length() < 2
                || request.quote().locality().trim().length() > 120
                || !request.quote().postalCode().matches("[0-9]{6}")
                || request.quote().items() == null || request.quote().items().isEmpty()
                || request.quote().items().size() > 50
                || request.quote().items().stream().anyMatch(item -> item == null
                    || item.productId() == null || item.productId() <= 0)
                || request.quote().latitude() == null
                || request.quote().longitude() == null || !Double.isFinite(request.quote().latitude())
                || !Double.isFinite(request.quote().longitude())
                || Math.abs(request.quote().latitude()) > 90 || Math.abs(request.quote().longitude()) > 180
                || request.customerName() == null || request.customerName().trim().length() < 2
                || request.customerName().trim().length() > 150
                || request.customerPhone() == null || !request.customerPhone().matches("[6-9][0-9]{9}")
                || request.addressLine() == null || request.addressLine().isBlank()
                || request.addressLine().trim().length() > 300)
            throw new IllegalArgumentException("Complete delivery order details are required.");
    }

    private static String fingerprint(CreateRequest request) {
        StringBuilder canonical = new StringBuilder("delivery-v1|");
        var quote = request.quote();
        append(canonical, String.valueOf(quote.branchId()));
        append(canonical, String.valueOf(request.windowId()));
        append(canonical, quote.serviceDate().toString());
        append(canonical, quote.locality().trim());
        append(canonical, quote.postalCode());
        append(canonical, String.valueOf(quote.latitude()));
        append(canonical, String.valueOf(quote.longitude()));
        append(canonical, request.customerName().trim());
        append(canonical, request.customerPhone());
        append(canonical, request.addressLine().trim());
        quote.items().stream().sorted(Comparator.comparing(item -> item.productId() == null ? -1L : item.productId()))
                .forEach(item -> {
                    append(canonical, String.valueOf(item.productId()));
                    append(canonical, String.valueOf(item.quantity()));
                    append(canonical, String.valueOf(item.weightGrams()));
                });
        return digest(canonical.toString());
    }

    private static void append(StringBuilder value, String part) {
        value.append(part.length()).append(':').append(part);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static Created response(Order order) {
        return new Created(order.getId(), order.getOrderNumber(), order.getBranch().getId(),
                order.getDeliveryWindowId(), order.getOrderStatus(), order.getTotalAmount(),
                order.getReservationExpiresAt(), order.getCreatedAt());
    }

    public record CreateRequest(DeliveryCapacityService.QuoteRequest quote, long windowId,
                                String customerName, String customerPhone, String addressLine,
                                String acceptedQuoteToken) {}
    public record Created(Long id, String orderNumber, Long branchId, Long windowId,
                          OrderStatus orderStatus, java.math.BigDecimal totalAmount,
                          LocalDateTime reservationExpiresAt, LocalDateTime createdAt) {}
}
