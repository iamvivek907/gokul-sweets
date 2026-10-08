package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * Attempts share the locked order transaction; rejected guesses commit before any handover writes.
 */
@Service
@RequiredArgsConstructor
public class PickupCodeAttempts {

    private final JdbcTemplate jdbc;

    /**
     * Checks the operation.
     *
     * @param orderId the order id
     * @param submitted the submitted
     * @return the check result
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String check(long orderId, String submitted) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PickupCodeAttempts.class, "check(long,String)");
        try {
            var rows =
                    jdbc.query(
                            "SELECT code,failed_attempts,locked_until,consumed_at FROM"
                                    + " order_pickup_codes WHERE order_id=? FOR UPDATE",
                            (rs, n) ->
                                    new Object[] {
                                        rs.getString(1),
                                        rs.getInt(2),
                                        rs.getTimestamp(3),
                                        rs.getTimestamp(4)
                                    },
                            orderId);
            if (rows.isEmpty())
                return "Ask the customer to open their order and show the pickup code first.";
            var row = rows.getFirst();
            if (row[3] != null) return "This pickup code has already been used.";
            var lock = (java.sql.Timestamp) row[2];
            if (lock != null && lock.toInstant().isAfter(Instant.now()))
                return "Too many incorrect pickup codes. Try again in 15 minutes.";
            int failures = lock != null ? 0 : (Integer) row[1];
            if (submitted != null
                    && submitted.matches("[0-9]{4}")
                    && MessageDigest.isEqual(
                            ((String) row[0]).getBytes(StandardCharsets.US_ASCII),
                            submitted.getBytes(StandardCharsets.US_ASCII))) {
                jdbc.update(
                        "UPDATE order_pickup_codes SET failed_attempts=0,locked_until=NULL WHERE"
                                + " order_id=?",
                        orderId);
                return null;
            }
            failures++;
            jdbc.update(
                    "UPDATE order_pickup_codes SET failed_attempts=?,locked_until=CASE WHEN ?>=5"
                            + " THEN CURRENT_TIMESTAMP+INTERVAL '15 minutes' ELSE NULL END WHERE"
                            + " order_id=?",
                    failures,
                    failures,
                    orderId);
            return failures >= 5
                    ? "Too many incorrect pickup codes. Try again in 15 minutes."
                    : "Incorrect pickup code. Check the four digits with the customer.";
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PickupCodeAttempts.class, "check(long,String)");
        }
    }
}
