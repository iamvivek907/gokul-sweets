package com.gokulsweets.restaurant.order.service;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderIdempotencyService {

    private final OrderIdempotencyRepository
            orderIdempotencyRepository;

    public String createRequestHash(
            CreateOrderRequest request
    ) {

        StringBuilder canonical =
                createRequestPrefix(
                        request
                );

        if (requiresExtendedItemFormat(request)) {

            appendWeightedItems(
                    canonical,
                    request
            );

        } else {

            /*
             * Keep the original unit-only representation.
             *
             * Existing idempotency records may have been created
             * before weight-based ordering was introduced. Keeping
             * this format means a legitimate retry continues to
             * produce the same request hash after deployment.
             */
            appendLegacyUnitItems(
                    canonical,
                    request
            );
        }

        return sha256(
                canonical.toString()
        );
    }

    private StringBuilder createRequestPrefix(
            CreateOrderRequest request
    ) {

        return new StringBuilder()
                .append(request.branchId())
                .append('|')

                .append(request.pickupSlotId())
                .append('|')

                .append(normalize(
                        request.customerName()
                ))
                .append('|')

                .append(normalize(
                        request.customerPhone()
                ))
                .append('|')

                .append(request.pickupType())
                .append('|');
    }

    private boolean requiresExtendedItemFormat(
            CreateOrderRequest request
    ) {

        return request.items()
                .stream()
                .anyMatch(
                        item ->
                                item.quantity()
                                        == null
                                        ||
                                        item.weightGrams()
                                                != null
                );
    }

    private void appendLegacyUnitItems(
            StringBuilder canonical,
            CreateOrderRequest request
    ) {

        Map<Long, Integer> normalizedItems =
                new TreeMap<>();

        for (CreateOrderItemRequest item :
                request.items()) {

            normalizedItems.merge(
                    item.productId(),
                    item.quantity(),
                    Math::addExact
            );
        }

        normalizedItems.forEach(
                (productId, quantity) ->
                        canonical
                                .append(productId)
                                .append(':')
                                .append(quantity)
                                .append(';')
        );
    }

    private void appendWeightedItems(
            StringBuilder canonical,
            CreateOrderRequest request
    ) {

        Map<Long, ItemTotals> normalizedItems =
                new TreeMap<>();

        for (CreateOrderItemRequest item :
                request.items()) {

            ItemTotals totals =
                    normalizedItems.computeIfAbsent(
                            item.productId(),
                            ignored ->
                                    new ItemTotals()
                    );

            totals.add(
                    item.quantity(),
                    item.weightGrams()
            );
        }

        normalizedItems.forEach(
                (productId, totals) ->
                        canonical
                                .append(productId)
                                .append(":q=")
                                .append(nullableNumber(
                                        totals.quantity
                                ))
                                .append(",w=")
                                .append(nullableNumber(
                                        totals.weightGrams
                                ))
                                .append(';')
        );
    }

    private String nullableNumber(
            Integer value
    ) {

        return value == null
                ? "-"
                : value.toString();
    }

    public ClaimResult claim(
            String idempotencyKey,
            String requestHash
    ) {

        validateKey(
                idempotencyKey
        );

        int inserted =
                orderIdempotencyRepository.claim(
                        idempotencyKey,
                        requestHash
                );

        /*
         * We successfully claimed the key.
         */
        if (inserted == 1) {

            log.debug(
                    "Order idempotency key claimed: key={}",
                    maskKey(idempotencyKey)
            );

            return ClaimResult.NEW;
        }

        /*
         * Existing key.
         */
        OrderIdempotency existing =
                orderIdempotencyRepository
                        .findByIdempotencyKey(
                                idempotencyKey
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Unable to resolve existing idempotency request."
                                )
                        );

        if (!existing.getRequestHash()
                .equals(requestHash)) {

            log.warn(
                    "Idempotency key reused with different order request: key={}",
                    maskKey(idempotencyKey)
            );

            throw new IllegalStateException(
                    "This idempotency key was already used for a different order request."
            );
        }

        if (existing.getOrder() == null) {

            /*
             * This should be rare because PostgreSQL normally
             * waits for the competing transaction to finish
             * before ON CONFLICT resolves.
             */
            log.warn(
                    "Existing idempotency request has no completed order yet: key={}",
                    maskKey(idempotencyKey)
            );

            throw new IllegalStateException(
                    "This order request is already being processed."
            );
        }

        log.info(
                "Returning existing order for duplicate request: orderId={}, key={}",
                existing.getOrder().getId(),
                maskKey(idempotencyKey)
        );

        return new ClaimResult(
                existing.getOrder()
        );
    }

    public void linkOrder(
            String idempotencyKey,
            Order order
    ) {

        int updated =
                orderIdempotencyRepository
                        .linkOrder(
                                idempotencyKey,
                                order.getId()
                        );

        if (updated != 1) {

            throw new IllegalStateException(
                    "Unable to link idempotency key to order."
            );
        }

        log.debug(
                "Order linked to idempotency key: orderId={}, key={}",
                order.getId(),
                maskKey(idempotencyKey)
        );
    }

    private void validateKey(
            String idempotencyKey
    ) {

        if (idempotencyKey == null
                || idempotencyKey.isBlank()) {

            throw new IllegalArgumentException(
                    "Idempotency-Key header is required."
            );
        }

        if (idempotencyKey.length() > 100) {

            throw new IllegalArgumentException(
                    "Idempotency-Key must not exceed 100 characters."
            );
        }
    }

    private String normalize(
            String value
    ) {

        return value == null
                ? ""
                : value.trim();
    }

    private String sha256(
            String value
    ) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            value.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return toHex(hash);

        } catch (NoSuchAlgorithmException ex) {

            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    ex
            );
        }
    }

    private String toHex(
            byte[] bytes
    ) {

        StringBuilder result =
                new StringBuilder(
                        bytes.length * 2
                );

        for (byte value : bytes) {

            result.append(
                    String.format(
                            "%02x",
                            value & 0xff
                    )
            );
        }

        return result.toString();
    }

    private String maskKey(
            String key
    ) {

        if (key == null
                || key.length() <= 8) {

            return "***";
        }

        return key.substring(
                0,
                8
        ) + "...";
    }

    private static final class ItemTotals {

        private Integer quantity;

        private Integer weightGrams;

        private void add(
                Integer itemQuantity,
                Integer itemWeightGrams
        ) {

            if (itemQuantity != null) {

                quantity =
                        quantity == null
                                ? itemQuantity
                                : Math.addExact(
                                quantity,
                                itemQuantity
                        );
            }

            if (itemWeightGrams != null) {

                weightGrams =
                        weightGrams == null
                                ? itemWeightGrams
                                : Math.addExact(
                                weightGrams,
                                itemWeightGrams
                        );
            }
        }
    }

    public record ClaimResult(
            boolean newRequest,
            Order existingOrder
    ) {

        public static final ClaimResult NEW =
                new ClaimResult(
                        true,
                        null
                );

        public ClaimResult(
                Order existingOrder
        ) {
            this(
                    false,
                    existingOrder
            );
        }
    }
}
