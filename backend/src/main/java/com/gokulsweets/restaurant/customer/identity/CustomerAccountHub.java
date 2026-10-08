package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Account data is scoped to the exact verified subject, never recovered by phone. */
@Service
@RequiredArgsConstructor
public class CustomerAccountHub {

    private final JdbcTemplate jdbc;

    /** Immutable address data contract. */
    public record Address(
            long id, String label, String addressLine, String locality, String postalCode) {}

    /** Immutable preferences data contract. */
    public record Preferences(String dietaryNotes, Long preferredBranchId) {}

    /** Immutable snapshot data contract. */
    public record Snapshot(
            long paidOrders,
            List<Long> favouriteProductIds,
            List<Address> addresses,
            Preferences preferences,
            long completedOrders) {}

    /** Immutable address input data contract. */
    public record AddressInput(
            String label, String addressLine, String locality, String postalCode) {}

    /**
     * Snapshots the operation.
     *
     * @param environment the environment
     * @param subject the subject
     * @return the snapshot result
     */
    @Transactional(readOnly = true)
    public Snapshot snapshot(String environment, UUID subject) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerAccountHub.class, "snapshot(String,UUID)");
        try {
            Long paid =
                    jdbc.queryForObject(
                            """
SELECT COUNT(*) FROM verified_order_ownership owner
JOIN orders o ON o.id = owner.order_id
WHERE owner.environment = ? AND owner.verified_subject_id = ?
  AND o.order_status NOT IN ('PENDING_PAYMENT', 'PAYMENT_FAILED', 'CANCELLED')
  AND EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.id AND p.payment_status = 'PAID')
  AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.id
                  AND p.payment_status IN ('REFUNDED', 'REFUND_PENDING'))
""",
                            Long.class,
                            environment,
                            subject);
            var favourites =
                    jdbc.query(
                            """
SELECT f.product_id FROM verified_customer_favourites f
WHERE f.environment = ? AND f.subject_id = ? ORDER BY f.created_at DESC, f.product_id DESC
""",
                            (rs, row) -> rs.getLong(1),
                            environment,
                            subject);
            var addresses =
                    jdbc.query(
                            """
SELECT id, label, address_line, locality, postal_code FROM verified_customer_addresses
WHERE environment = ? AND subject_id = ? ORDER BY id DESC
""",
                            (rs, row) ->
                                    new Address(
                                            rs.getLong(1),
                                            rs.getString(2),
                                            rs.getString(3),
                                            rs.getString(4),
                                            rs.getString(5)),
                            environment,
                            subject);
            var preferences =
                    jdbc
                            .query(
                                    """
SELECT dietary_notes, preferred_branch_id FROM verified_customer_account
WHERE environment = ? AND subject_id = ?
""",
                                    (rs, row) ->
                                            new Preferences(
                                                    rs.getString(1), rs.getObject(2, Long.class)),
                                    environment,
                                    subject)
                            .stream()
                            .findFirst()
                            .orElse(new Preferences(null, null));
            return new Snapshot(
                    paid == null ? 0 : paid,
                    favourites,
                    addresses,
                    preferences,
                    new CustomerVisitPolicy(jdbc).completed(environment, subject));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerAccountHub.class, "snapshot(String,UUID)");
        }
    }

    /**
     * Saves preferences.
     *
     * @param environment the environment
     * @param subject the subject
     * @param value the value
     */
    @Transactional
    public void savePreferences(String environment, UUID subject, Preferences value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerAccountHub.class, "savePreferences(String,UUID,Preferences)");
        try {
            if (value == null
                    || value.dietaryNotes() != null && value.dietaryNotes().length() > 300)
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Dietary notes must be 300 characters or fewer");
            if (value.preferredBranchId() != null
                    && !Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    "SELECT EXISTS (SELECT 1 FROM branches WHERE id = ? AND active"
                                            + " = TRUE)",
                                    Boolean.class,
                                    value.preferredBranchId())))
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Choose an active branch");
            lockSubject(environment, subject);
            jdbc.update(
                    """
INSERT INTO verified_customer_account(environment, subject_id, dietary_notes, preferred_branch_id)
VALUES (?, ?, ?, ?) ON CONFLICT (environment, subject_id)
DO UPDATE SET dietary_notes = EXCLUDED.dietary_notes,
              preferred_branch_id = EXCLUDED.preferred_branch_id
""",
                    environment,
                    subject,
                    value.dietaryNotes() == null ? null : value.dietaryNotes().trim(),
                    value.preferredBranchId());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerAccountHub.class,
                    "savePreferences(String,UUID,Preferences)");
        }
    }

    /**
     * Updates favourite.
     *
     * @param environment the environment
     * @param subject the subject
     * @param productId the product id
     * @param saved the saved
     */
    @Transactional
    public void setFavourite(String environment, UUID subject, long productId, boolean saved) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerAccountHub.class, "setFavourite(String,UUID,long,boolean)");
        try {
            lockSubject(environment, subject);
            if (saved) {
                if (!Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT EXISTS (SELECT 1 FROM products WHERE id = ? AND active ="
                                        + " TRUE)",
                                Boolean.class,
                                productId)))
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                // A unique key makes repeated requests idempotent. Limit per account to avoid
                // unbounded lists.
                if (Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                """
                                SELECT COUNT(*) >= 30 FROM verified_customer_favourites
                                WHERE environment = ? AND subject_id = ? AND product_id <> ?
                                """,
                                Boolean.class,
                                environment,
                                subject,
                                productId)))
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Maximum 30 favourites");
                jdbc.update(
                        """
INSERT INTO verified_customer_favourites(environment, subject_id, product_id)
VALUES (?, ?, ?) ON CONFLICT DO NOTHING
""",
                        environment,
                        subject,
                        productId);
            } else
                jdbc.update(
                        """
DELETE FROM verified_customer_favourites WHERE environment = ? AND subject_id = ? AND product_id = ?
""",
                        environment,
                        subject,
                        productId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerAccountHub.class,
                    "setFavourite(String,UUID,long,boolean)");
        }
    }

    /**
     * Adds address.
     *
     * @param environment the environment
     * @param subject the subject
     * @param input the input
     * @return the add address result
     */
    @Transactional
    public Address addAddress(String environment, UUID subject, AddressInput input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerAccountHub.class, "addAddress(String,UUID,AddressInput)");
        try {
            validateAddress(input);
            lockSubject(environment, subject);
            if (Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            """
SELECT COUNT(*) >= 5 FROM verified_customer_addresses WHERE environment = ? AND subject_id = ?
""",
                            Boolean.class,
                            environment,
                            subject)))
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Maximum five saved addresses");
            return jdbc.queryForObject(
                    """
INSERT INTO verified_customer_addresses(environment, subject_id, label, address_line, locality, postal_code)
VALUES (?, ?, ?, ?, ?, ?) RETURNING id, label, address_line, locality, postal_code
""",
                    (rs, row) ->
                            new Address(
                                    rs.getLong(1),
                                    rs.getString(2),
                                    rs.getString(3),
                                    rs.getString(4),
                                    rs.getString(5)),
                    environment,
                    subject,
                    input.label().trim(),
                    input.addressLine().trim(),
                    input.locality().trim(),
                    input.postalCode());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerAccountHub.class,
                    "addAddress(String,UUID,AddressInput)");
        }
    }

    /**
     * Updates address.
     *
     * @param environment the environment
     * @param subject the subject
     * @param addressId the address id
     * @param input the input
     * @return the update address result
     */
    @Transactional
    public Address updateAddress(
            String environment, UUID subject, long addressId, AddressInput input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerAccountHub.class, "updateAddress(String,UUID,long,AddressInput)");
        try {
            validateAddress(input);
            lockSubject(environment, subject);
            var updated =
                    jdbc.query(
                            """
                            UPDATE verified_customer_addresses
                            SET label = ?, address_line = ?, locality = ?, postal_code = ?
                            WHERE id = ? AND environment = ? AND subject_id = ?
                            RETURNING id, label, address_line, locality, postal_code
                            """,
                            (rs, row) ->
                                    new Address(
                                            rs.getLong(1),
                                            rs.getString(2),
                                            rs.getString(3),
                                            rs.getString(4),
                                            rs.getString(5)),
                            input.label().trim(),
                            input.addressLine().trim(),
                            input.locality().trim(),
                            input.postalCode(),
                            addressId,
                            environment,
                            subject);
            if (updated.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            return updated.getFirst();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerAccountHub.class,
                    "updateAddress(String,UUID,long,AddressInput)");
        }
    }

    /**
     * Deletes address.
     *
     * @param environment the environment
     * @param subject the subject
     * @param addressId the address id
     */
    @Transactional
    public void deleteAddress(String environment, UUID subject, long addressId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerAccountHub.class, "deleteAddress(String,UUID,long)");
        try {
            lockSubject(environment, subject);
            if (jdbc.update(
                            """
DELETE FROM verified_customer_addresses WHERE id = ? AND environment = ? AND subject_id = ?
""",
                            addressId,
                            environment,
                            subject)
                    == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerAccountHub.class,
                    "deleteAddress(String,UUID,long)");
        }
    }

    /**
     * Valids the operation.
     *
     * @param text the text
     * @param max the max
     * @return the valid result
     */
    private boolean valid(String text, int max) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerAccountHub.class, "valid(String,int)");
        try {
            return text != null && !text.isBlank() && text.trim().length() <= max;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerAccountHub.class, "valid(String,int)");
        }
    }

    /**
     * Validates address.
     *
     * @param input the input
     */
    private void validateAddress(AddressInput input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerAccountHub.class, "validateAddress(AddressInput)");
        try {
            if (input == null
                    || !valid(input.label(), 40)
                    || !valid(input.addressLine(), 180)
                    || !valid(input.locality(), 100)
                    || input.postalCode() == null
                    || !input.postalCode().matches("[0-9]{6}"))
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Enter a valid address and six-digit postal code");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerAccountHub.class,
                    "validateAddress(AddressInput)");
        }
    }

    /**
     * Locks subject.
     *
     * @param environment the environment
     * @param subject the subject
     */
    private void lockSubject(String environment, UUID subject) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerAccountHub.class, "lockSubject(String,UUID)");
        try {
            // Serializes capped writes and refuses to save details for an obsolete subject.
            if (jdbc.query(
                            """
SELECT 1 FROM verified_customer_subjects WHERE environment = ? AND id = ? FOR UPDATE
""",
                            (rs, row) -> rs.getInt(1),
                            environment,
                            subject)
                    .isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerAccountHub.class,
                    "lockSubject(String,UUID)");
        }
    }
}
