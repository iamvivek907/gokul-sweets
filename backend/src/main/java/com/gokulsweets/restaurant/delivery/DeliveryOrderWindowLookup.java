package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

/** Resolve the order's own branch-scoped IST rider window for kitchen and print output. */
@Service
@RequiredArgsConstructor
public class DeliveryOrderWindowLookup {

    private final JdbcTemplate jdbc;

    /**
     * Requires the operation.
     *
     * @param order the order
     * @return the require result
     */
    @Transactional(readOnly = true)
    public Window require(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryOrderWindowLookup.class, "require(Order)");
        try {
            if (order == null
                    || order.getFulfillmentType() != FulfillmentType.DELIVERY
                    || order.getDeliveryWindowId() == null
                    || order.getBranch() == null)
                throw new IllegalStateException("Delivery order window is unavailable.");
            return jdbc
                    .query(
                            """
                            SELECT w.service_date, w.starts_at, w.ends_at
                            FROM delivery_capacity_windows w
                            JOIN delivery_zones z ON z.id = w.zone_id
                            WHERE w.id = ? AND z.branch_id = ?
                            """,
                            (rs, row) ->
                                    new Window(
                                            rs.getDate(1).toLocalDate(),
                                            rs.getTime(2).toLocalTime(),
                                            rs.getTime(3).toLocalTime()),
                            order.getDeliveryWindowId(),
                            order.getBranch().getId())
                    .stream()
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new IllegalStateException(
                                            "Delivery order window is unavailable."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, DeliveryOrderWindowLookup.class, "require(Order)");
        }
    }

    /**
     * Immutable window data contract.
     *
     * @param date the date
     * @param start the start
     * @param end the end
     */
    public record Window(LocalDate date, LocalTime start, LocalTime end) {}
}
