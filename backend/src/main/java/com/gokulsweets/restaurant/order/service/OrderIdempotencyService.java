package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.dto.CreateOrderRequest;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.entity.OrderIdempotency;
import com.gokulsweets.restaurant.order.repository.OrderIdempotencyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/** Coordinates order idempotency operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderIdempotencyService {

    private final OrderIdempotencyRepository orderIdempotencyRepository;

    /**
     * Creates request hash.
     *
     * @param request the request
     * @return the create request hash result
     */
    public String createRequestHash(CreateOrderRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderIdempotencyService.class, "createRequestHash(CreateOrderRequest)");
        try {
            StringBuilder canonical = createRequestPrefix(request);
            if (requiresExtendedItemFormat(request)) {
                appendWeightedItems(canonical, request);
            } else {
                /*
                 * Keep the original unit-only representation.
                 *
                 * Existing idempotency records may have been created
                 * before weight-based ordering was introduced. Keeping
                 * this format means a legitimate retry continues to
                 * produce the same request hash after deployment.
                 */
                appendLegacyUnitItems(canonical, request);
            }
            // Preserve historical hashes when neither selection is present. Quote tokens
            // are intentionally excluded: refreshing a quote must still retry this order.
            appendSelection(canonical, "reward", normalize(request.rewardCode()));
            appendSelection(
                    canonical,
                    "offer",
                    normalize(request.offerCode()).toUpperCase(java.util.Locale.ROOT));
            return sha256(canonical.toString());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderIdempotencyService.class,
                    "createRequestHash(CreateOrderRequest)");
        }
    }

    /**
     * Appends selection.
     *
     * @param canonical the canonical
     * @param kind the kind
     * @param selection the selection
     */
    private void appendSelection(StringBuilder canonical, String kind, String selection) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderIdempotencyService.class,
                        "appendSelection(StringBuilder,String,String)");
        try {
            if (!selection.isEmpty()) {
                canonical
                        .append('|')
                        .append(kind)
                        .append(':')
                        .append(selection.length())
                        .append(':')
                        .append(selection);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderIdempotencyService.class,
                    "appendSelection(StringBuilder,String,String)");
        }
    }

    /**
     * Creates request prefix.
     *
     * @param request the request
     * @return the create request prefix result
     */
    private StringBuilder createRequestPrefix(CreateOrderRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderIdempotencyService.class, "createRequestPrefix(CreateOrderRequest)");
        try {
            return new StringBuilder()
                    .append(request.branchId())
                    .append('|')
                    .append(request.pickupSlotId())
                    .append('|')
                    .append(normalize(request.customerName()))
                    .append('|')
                    .append(normalize(request.customerPhone()))
                    .append('|')
                    .append(request.pickupType())
                    .append('|');
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderIdempotencyService.class,
                    "createRequestPrefix(CreateOrderRequest)");
        }
    }

    /**
     * Requireses extended item format.
     *
     * @param request the request
     * @return the requires extended item format result
     */
    private boolean requiresExtendedItemFormat(CreateOrderRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderIdempotencyService.class,
                        "requiresExtendedItemFormat(CreateOrderRequest)");
        try {
            return request.items().stream()
                    .anyMatch(item -> item.quantity() == null || item.weightGrams() != null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderIdempotencyService.class,
                    "requiresExtendedItemFormat(CreateOrderRequest)");
        }
    }

    /**
     * Appends legacy unit items.
     *
     * @param canonical the canonical
     * @param request the request
     */
    private void appendLegacyUnitItems(StringBuilder canonical, CreateOrderRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderIdempotencyService.class,
                        "appendLegacyUnitItems(StringBuilder,CreateOrderRequest)");
        try {
            Map<Long, Integer> normalizedItems = new TreeMap<>();
            for (CreateOrderItemRequest item : request.items()) {
                normalizedItems.merge(item.productId(), item.quantity(), Math::addExact);
            }
            normalizedItems.forEach(
                    (productId, quantity) ->
                            canonical.append(productId).append(':').append(quantity).append(';'));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderIdempotencyService.class,
                    "appendLegacyUnitItems(StringBuilder,CreateOrderRequest)");
        }
    }

    /**
     * Appends weighted items.
     *
     * @param canonical the canonical
     * @param request the request
     */
    private void appendWeightedItems(StringBuilder canonical, CreateOrderRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderIdempotencyService.class,
                        "appendWeightedItems(StringBuilder,CreateOrderRequest)");
        try {
            Map<Long, ItemTotals> normalizedItems = new TreeMap<>();
            for (CreateOrderItemRequest item : request.items()) {
                ItemTotals totals =
                        normalizedItems.computeIfAbsent(
                                item.productId(), ignored -> new ItemTotals());
                totals.add(item.quantity(), item.weightGrams());
            }
            normalizedItems.forEach(
                    (productId, totals) ->
                            canonical
                                    .append(productId)
                                    .append(":q=")
                                    .append(nullableNumber(totals.quantity))
                                    .append(",w=")
                                    .append(nullableNumber(totals.weightGrams))
                                    .append(';'));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderIdempotencyService.class,
                    "appendWeightedItems(StringBuilder,CreateOrderRequest)");
        }
    }

    /**
     * Nullables number.
     *
     * @param value the value
     * @return the nullable number result
     */
    private String nullableNumber(Integer value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderIdempotencyService.class, "nullableNumber(Integer)");
        try {
            return value == null ? "-" : value.toString();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderIdempotencyService.class,
                    "nullableNumber(Integer)");
        }
    }

    /**
     * Claims the operation.
     *
     * @param idempotencyKey the idempotency key
     * @param requestHash the request hash
     * @return the claim result
     */
    public ClaimResult claim(String idempotencyKey, String requestHash) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderIdempotencyService.class, "claim(String,String)");
        try {
            validateKey(idempotencyKey);
            int inserted = orderIdempotencyRepository.claim(idempotencyKey, requestHash);
            /*
             * We successfully claimed the key.
             */
            if (inserted == 1) {
                log.debug("Order idempotency key claimed: key={}", maskKey(idempotencyKey));
                return ClaimResult.NEW;
            }
            /*
             * Existing key.
             */
            OrderIdempotency existing =
                    orderIdempotencyRepository
                            .findByIdempotencyKey(idempotencyKey)
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Unable to resolve existing idempotency"
                                                            + " request."));
            if (!existing.getRequestHash().equals(requestHash)) {
                log.warn(
                        "Idempotency key reused with different order request: key={}",
                        maskKey(idempotencyKey));
                throw new IllegalStateException(
                        "This idempotency key was already used for a different order request.");
            }
            if (existing.getOrder() == null) {
                /*
                 * This should be rare because PostgreSQL normally
                 * waits for the competing transaction to finish
                 * before ON CONFLICT resolves.
                 */
                log.warn(
                        "Existing idempotency request has no completed order yet: key={}",
                        maskKey(idempotencyKey));
                throw new IllegalStateException("This order request is already being processed.");
            }
            log.info(
                    "Returning existing order for duplicate request: orderId={}, key={}",
                    existing.getOrder().getId(),
                    maskKey(idempotencyKey));
            return new ClaimResult(existing.getOrder());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderIdempotencyService.class,
                    "claim(String,String)");
        }
    }

    /**
     * Links order.
     *
     * @param idempotencyKey the idempotency key
     * @param order the order
     */
    public void linkOrder(String idempotencyKey, Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderIdempotencyService.class, "linkOrder(String,Order)");
        try {
            int updated = orderIdempotencyRepository.linkOrder(idempotencyKey, order.getId());
            if (updated != 1) {
                throw new IllegalStateException("Unable to link idempotency key to order.");
            }
            log.debug(
                    "Order linked to idempotency key: orderId={}, key={}",
                    order.getId(),
                    maskKey(idempotencyKey));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderIdempotencyService.class,
                    "linkOrder(String,Order)");
        }
    }

    /**
     * Validates key.
     *
     * @param idempotencyKey the idempotency key
     */
    private void validateKey(String idempotencyKey) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderIdempotencyService.class, "validateKey(String)");
        try {
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("Idempotency-Key header is required.");
            }
            if (idempotencyKey.length() > 100) {
                throw new IllegalArgumentException(
                        "Idempotency-Key must not exceed 100 characters.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderIdempotencyService.class,
                    "validateKey(String)");
        }
    }

    /**
     * Normalizes the operation.
     *
     * @param value the value
     * @return the normalize result
     */
    private String normalize(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderIdempotencyService.class, "normalize(String)");
        try {
            return value == null ? "" : value.trim();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OrderIdempotencyService.class, "normalize(String)");
        }
    }

    /**
     * Sha256s the operation.
     *
     * @param value the value
     * @return the sha256 result
     */
    private String sha256(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderIdempotencyService.class, "sha256(String)");
        try {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
                return toHex(hash);
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("SHA-256 is unavailable.", ex);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OrderIdempotencyService.class, "sha256(String)");
        }
    }

    /**
     * Tos hex.
     *
     * @param bytes the bytes
     * @return the to hex result
     */
    private String toHex(byte[] bytes) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderIdempotencyService.class, "toHex(byte[])");
        try {
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OrderIdempotencyService.class, "toHex(byte[])");
        }
    }

    /**
     * Masks key.
     *
     * @param key the key
     * @return the mask key result
     */
    private String maskKey(String key) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderIdempotencyService.class, "maskKey(String)");
        try {
            if (key == null || key.length() <= 8) {
                return "***";
            }
            return key.substring(0, 8) + "...";
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OrderIdempotencyService.class, "maskKey(String)");
        }
    }

    /** Backend item totals contract and implementation. */
    private static final class ItemTotals {

        private Integer quantity;

        private Integer weightGrams;

        /**
         * Adds the operation.
         *
         * @param itemQuantity the item quantity
         * @param itemWeightGrams the item weight grams
         */
        private void add(Integer itemQuantity, Integer itemWeightGrams) {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(
                            OrderIdempotencyService.ItemTotals.class, "add(Integer,Integer)");
            try {
                if (itemQuantity != null) {
                    quantity =
                            quantity == null ? itemQuantity : Math.addExact(quantity, itemQuantity);
                }
                if (itemWeightGrams != null) {
                    weightGrams =
                            weightGrams == null
                                    ? itemWeightGrams
                                    : Math.addExact(weightGrams, itemWeightGrams);
                }
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos,
                        OrderIdempotencyService.ItemTotals.class,
                        "add(Integer,Integer)");
            }
        }
    }

    /**
     * Immutable claim result data contract.
     *
     * @param newRequest the new request
     * @param existingOrder the existing order
     */
    public record ClaimResult(boolean newRequest, Order existingOrder) {

        /** The new value. */
        public static final ClaimResult NEW = new ClaimResult(true, null);

        /**
         * Creates a claim result instance.
         *
         * @param existingOrder the existing order
         */
        public ClaimResult(Order existingOrder) {
            this(false, existingOrder);
        }
    }
}
