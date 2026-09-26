package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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

    public void bindNewOrder(Long orderId, String checkoutPhone, String token) {
        if (!features.isCustomerOtpIdentity() || token == null || !token.matches("[0-9a-f]{64}")
                || checkoutPhone == null || !checkoutPhone.matches("[6-9][0-9]{9}")
                || !settings.getProperty("gokul.environment-isolation.enabled", Boolean.class, false)
                || !settings.getProperty("gokul.web.environment-cors-enabled", Boolean.class, false)) return;
        String environment = settings.getProperty("gokul.environment-isolation.environment", "");
        if (!"DEV".equals(environment) && !"PROD".equals(environment)) return;
        // Lock the session and current subject until the enclosing order transaction commits.
        // Reverification rotates the subject and revokes old sessions under a row update lock.
        jdbc.update("""
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
                """, environment, VerifiedCustomerSessionStore.digest(token), Timestamp.from(Instant.now()),
                "+91" + checkoutPhone, orderId, environment, Timestamp.from(Instant.now()));
    }

    /** A current subject sees only orders bound while that exact subject was live. */
    public List<String> orderNumbers(String environment, UUID subjectId) {
        return jdbc.query("""
                SELECT o.order_number FROM verified_order_ownership ownership
                JOIN orders o ON o.id = ownership.order_id
                WHERE ownership.environment = ? AND ownership.verified_subject_id = ?
                ORDER BY o.created_at DESC, o.id DESC LIMIT 500
                """, (rs, row) -> rs.getString(1), environment, subjectId);
    }

    public boolean owns(String environment, UUID subjectId, String orderNumber) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM verified_order_ownership ownership
                    JOIN orders o ON o.id = ownership.order_id
                    WHERE ownership.environment = ? AND ownership.verified_subject_id = ?
                      AND o.order_number = ?
                )
                """, Boolean.class, environment, subjectId, orderNumber));
    }
}
