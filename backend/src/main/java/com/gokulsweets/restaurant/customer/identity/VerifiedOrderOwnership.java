package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** New-order binding only; never imports guest or historical orders by phone. */
@Service
@RequiredArgsConstructor
public class VerifiedOrderOwnership {

    private final JdbcTemplate jdbc;

    private final EnhancementProperties features;

    private final Environment settings;

    /**
     * Runs inside checkout's transaction before claiming an idempotency key or reserving stock.
     *
     * @param checkoutPhone the checkout phone
     * @param token the token
     */
    public void requireCheckoutIdentity(String checkoutPhone, String token) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        VerifiedOrderOwnership.class, "requireCheckoutIdentity(String,String)");
        try {
            if (settings.getProperty("gokul.checkout.guest-enabled", Boolean.class, true)) return;
            requireVerifiedIdentity(checkoutPhone, token);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    VerifiedOrderOwnership.class,
                    "requireCheckoutIdentity(String,String)");
        }
    }

    /**
     * Customer-specific draft offers always require a real verified session.
     *
     * @param checkoutPhone the checkout phone
     * @param token the token
     */
    public void requireVerifiedIdentity(String checkoutPhone, String token) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        VerifiedOrderOwnership.class, "requireVerifiedIdentity(String,String)");
        try {
            if (!canBind(checkoutPhone, token)) throw signInRequired();
            String environment =
                    settings.getProperty("gokul.environment-isolation.environment", "");
            var subjects =
                    jdbc.queryForList(
                            """
SELECT s.verified_subject_id FROM verified_customer_sessions s
JOIN verified_customer_subjects v ON v.environment=s.environment AND v.id=s.verified_subject_id
WHERE s.environment=? AND s.token_digest=? AND s.revoked_at IS NULL AND s.expires_at>?
  AND v.verified_phone=? FOR SHARE OF s,v
""",
                            UUID.class,
                            environment,
                            VerifiedCustomerSessionStore.digest(token),
                            Timestamp.from(Instant.now()),
                            "+91" + checkoutPhone);
            if (subjects.isEmpty()) throw signInRequired();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    VerifiedOrderOwnership.class,
                    "requireVerifiedIdentity(String,String)");
        }
    }

    /**
     * Immutable subject data contract.
     *
     * @param environment the environment
     * @param id the id
     */
    public record Subject(String environment, UUID id) {}

    /**
     * Verifieds subject.
     *
     * @param phone the phone
     * @param token the token
     * @return the verified subject result
     */
    public Subject verifiedSubject(String phone, String token) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(VerifiedOrderOwnership.class, "verifiedSubject(String,String)");
        try {
            requireVerifiedIdentity(phone, token);
            String environment =
                    settings.getProperty("gokul.environment-isolation.environment", "");
            UUID id =
                    jdbc.queryForObject(
                            "SELECT verified_subject_id FROM verified_customer_sessions WHERE"
                                    + " environment=? AND token_digest=? AND revoked_at IS NULL AND"
                                    + " expires_at>?",
                            UUID.class,
                            environment,
                            VerifiedCustomerSessionStore.digest(token),
                            Timestamp.from(Instant.now()));
            return new Subject(environment, id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    VerifiedOrderOwnership.class,
                    "verifiedSubject(String,String)");
        }
    }

    /**
     * Requires checkout replay.
     *
     * @param orderId the order id
     * @param token the token
     */
    public void requireCheckoutReplay(Long orderId, String token) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        VerifiedOrderOwnership.class, "requireCheckoutReplay(Long,String)");
        try {
            if (settings.getProperty("gokul.checkout.guest-enabled", Boolean.class, true)) return;
            if (token == null || !token.matches("[0-9a-f]{64}")) throw signInRequired();
            String environment =
                    settings.getProperty("gokul.environment-isolation.environment", "");
            boolean owned =
                    Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    """
SELECT EXISTS (SELECT 1 FROM verified_order_ownership own
JOIN verified_customer_sessions s ON s.environment=own.environment AND s.verified_subject_id=own.verified_subject_id
WHERE own.order_id=? AND own.environment=? AND s.token_digest=?
  AND s.revoked_at IS NULL AND s.expires_at>?)
""",
                                    Boolean.class,
                                    orderId,
                                    environment,
                                    VerifiedCustomerSessionStore.digest(token),
                                    Timestamp.from(Instant.now())));
            if (!owned) throw signInRequired();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    VerifiedOrderOwnership.class,
                    "requireCheckoutReplay(Long,String)");
        }
    }

    /**
     * Reports whether bind.
     *
     * @param phone the phone
     * @param token the token
     * @return the can bind result
     */
    private boolean canBind(String phone, String token) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(VerifiedOrderOwnership.class, "canBind(String,String)");
        try {
            String environment =
                    settings.getProperty("gokul.environment-isolation.environment", "");
            return features.isCustomerOtpIdentity()
                    && token != null
                    && token.matches("[0-9a-f]{64}")
                    && phone != null
                    && phone.matches("[6-9][0-9]{9}")
                    && settings.getProperty(
                            "gokul.environment-isolation.enabled", Boolean.class, false)
                    && settings.getProperty(
                            "gokul.web.environment-cors-enabled", Boolean.class, false)
                    && ("DEV".equals(environment) || "PROD".equals(environment));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    VerifiedOrderOwnership.class,
                    "canBind(String,String)");
        }
    }

    /**
     * Signs in required.
     *
     * @return the sign in required result
     */
    private ResponseStatusException signInRequired() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(VerifiedOrderOwnership.class, "signInRequired()");
        try {
            return new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Sign in with your verified phone before placing an order.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, VerifiedOrderOwnership.class, "signInRequired()");
        }
    }

    /**
     * Binds new order.
     *
     * @param orderId the order id
     * @param checkoutPhone the checkout phone
     * @param token the token
     */
    public void bindNewOrder(Long orderId, String checkoutPhone, String token) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        VerifiedOrderOwnership.class, "bindNewOrder(Long,String,String)");
        try {
            requireCheckoutIdentity(checkoutPhone, token);
            if (!canBind(checkoutPhone, token)) return;
            String environment =
                    settings.getProperty("gokul.environment-isolation.environment", "");
            // Lock the session and current subject until the enclosing order transaction commits.
            // Reverification rotates the subject and revokes old sessions under a row update lock.
            int bound =
                    jdbc.update(
                            """
                            WITH verified AS (
                                SELECT s.verified_subject_id
                                FROM verified_customer_sessions s
                                JOIN verified_customer_subjects v
                                  ON v.environment = s.environment AND v.id = s.verified_subject_id
                                WHERE s.environment = ? AND s.token_digest = ?
                                  AND s.revoked_at IS NULL AND s.expires_at > ?
                                  AND v.verified_phone = ?
                                FOR SHARE OF s, v
                            )
                            INSERT INTO verified_order_ownership
                                (order_id, environment, verified_subject_id, bound_at)
                            SELECT ?, ?, verified_subject_id, ? FROM verified
                            """,
                            environment,
                            VerifiedCustomerSessionStore.digest(token),
                            Timestamp.from(Instant.now()),
                            "+91" + checkoutPhone,
                            orderId,
                            environment,
                            Timestamp.from(Instant.now()));
            if (bound != 1
                    && !settings.getProperty("gokul.checkout.guest-enabled", Boolean.class, true))
                throw signInRequired();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    VerifiedOrderOwnership.class,
                    "bindNewOrder(Long,String,String)");
        }
    }

    /**
     * A current subject sees only orders bound while that exact subject was live.
     *
     * @param environment the environment
     * @param subjectId the subject id
     * @return the operation result
     */
    public List<String> orderNumbers(String environment, UUID subjectId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(VerifiedOrderOwnership.class, "orderNumbers(String,UUID)");
        try {
            return jdbc.query(
                    """
                    SELECT o.order_number FROM verified_order_ownership ownership
                    JOIN orders o ON o.id = ownership.order_id
                    WHERE ownership.environment = ? AND ownership.verified_subject_id = ?
                    ORDER BY o.created_at DESC, o.id DESC LIMIT 500
                    """,
                    (rs, row) -> rs.getString(1),
                    environment,
                    subjectId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    VerifiedOrderOwnership.class,
                    "orderNumbers(String,UUID)");
        }
    }

    /**
     * Stable owner-scoped cursor: new orders do not shift subsequent pages.
     *
     * @param environment the environment
     * @param subjectId the subject id
     * @param before the before
     * @param limit the limit
     * @return the operation result
     */
    public List<String> orderNumberPage(
            String environment, UUID subjectId, String before, int limit) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        VerifiedOrderOwnership.class, "orderNumberPage(String,UUID,String,int)");
        try {
            if (limit < 1
                    || limit > 50
                    || before != null
                            && (before.length() > 100 || !owns(environment, subjectId, before)))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid history page.");
            return jdbc.query(
                    """
                    SELECT o.order_number FROM verified_order_ownership ownership
                    JOIN orders o ON o.id = ownership.order_id
                    WHERE ownership.environment = ? AND ownership.verified_subject_id = ?
                      AND (CAST(? AS TEXT) IS NULL OR (o.created_at, o.id) <
                          (SELECT c.created_at, c.id FROM orders c WHERE c.order_number = ?))
                    ORDER BY o.created_at DESC, o.id DESC LIMIT ?
                    """,
                    (rs, row) -> rs.getString(1),
                    environment,
                    subjectId,
                    before,
                    before,
                    limit + 1);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    VerifiedOrderOwnership.class,
                    "orderNumberPage(String,UUID,String,int)");
        }
    }

    /**
     * Returns owns information for verified order ownership.
     *
     * <p>Reads {@code orders}, {@code verified_order_ownership}.
     *
     * @param environment the environment supplied to this method
     * @param subjectId the subject id supplied to this method
     * @param orderNumber the order number supplied to this method
     * @return the {@code boolean} result
     */
    public boolean owns(String environment, UUID subjectId, String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(VerifiedOrderOwnership.class, "owns(String,UUID,String)");
        try {
            return Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            """
SELECT EXISTS (
    SELECT 1 FROM verified_order_ownership ownership
    JOIN orders o ON o.id = ownership.order_id
    WHERE ownership.environment = ? AND ownership.verified_subject_id = ?
      AND o.order_number = ?
)
""",
                            Boolean.class,
                            environment,
                            subjectId,
                            orderNumber));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    VerifiedOrderOwnership.class,
                    "owns(String,UUID,String)");
        }
    }
}
