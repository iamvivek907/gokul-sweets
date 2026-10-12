package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.config.PreparationWindowProperties;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Branch-scoped delivery queue, using the same IST rider-window lead as preparation eligibility.
 */
@Service
@RequiredArgsConstructor
public class DeliveryPreparationQueue {

    private static final String FROM = AppConstant.DELIVERY_PREPARATION_QUEUE_FROM;

    private final JdbcTemplate jdbc;

    private final OrderRepository orders;

    private final PreparationWindowProperties preparation;

    /**
     * Returns eligible information for delivery preparation queue.
     *
     * @param branchId the branch id supplied to this method
     * @param now the now supplied to this method
     * @param limit the limit supplied to this method
     * @return the {@code List<Order>} result
     */
    public List<Order> eligible(Long branchId, LocalDateTime now, int limit) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DeliveryPreparationQueue.class, "eligible(Long,LocalDateTime,int)");
        try {
            LocalDateTime cutoff = cutoff(now);
            List<Long> ids =
                    jdbc.query(
                            "SELECT o.id "
                                    + FROM
                                    + """
                                      AND (w.service_date, w.starts_at) <= (?, ?)
                                      ORDER BY w.service_date, w.starts_at, o.created_at, o.id
                                      LIMIT ?
                                      """,
                            (rs, row) -> rs.getLong(1),
                            branchId,
                            cutoff.toLocalDate(),
                            cutoff.toLocalTime(),
                            limit);
            if (ids.isEmpty()) return List.of();
            Map<Long, Order> byId =
                    orders.findAllById(ids).stream()
                            .collect(Collectors.toMap(Order::getId, Function.identity()));
            return ids.stream().map(byId::get).filter(order -> order != null).toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryPreparationQueue.class,
                    "eligible(Long,LocalDateTime,int)");
        }
    }

    /**
     * Returns confirmed information for delivery preparation queue.
     *
     * @param branchId the branch id supplied to this method
     * @return the {@code long} result
     */
    public long confirmed(Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryPreparationQueue.class, "confirmed(Long)");
        try {
            return jdbc.queryForObject("SELECT COUNT(*) " + FROM, Long.class, branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, DeliveryPreparationQueue.class, "confirmed(Long)");
        }
    }

    /**
     * Eligibles count.
     *
     * @param branchId the branch id
     * @param now the now
     * @return the eligible count result
     */
    public long eligibleCount(Long branchId, LocalDateTime now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DeliveryPreparationQueue.class, "eligibleCount(Long,LocalDateTime)");
        try {
            LocalDateTime cutoff = cutoff(now);
            return jdbc.queryForObject(
                    "SELECT COUNT(*) " + FROM + " AND (w.service_date, w.starts_at) <= (?, ?)",
                    Long.class,
                    branchId,
                    cutoff.toLocalDate(),
                    cutoff.toLocalTime());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryPreparationQueue.class,
                    "eligibleCount(Long,LocalDateTime)");
        }
    }

    /**
     * Overdues count.
     *
     * @param branchId the branch id
     * @param now the now
     * @return the overdue count result
     */
    public long overdueCount(Long branchId, LocalDateTime now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DeliveryPreparationQueue.class, "overdueCount(Long,LocalDateTime)");
        try {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) " + FROM + " AND (w.service_date, w.starts_at) <= (?, ?)",
                    Long.class,
                    branchId,
                    now.toLocalDate(),
                    now.toLocalTime());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryPreparationQueue.class,
                    "overdueCount(Long,LocalDateTime)");
        }
    }

    /**
     * Returns cutoff information for delivery preparation queue.
     *
     * @param now the now supplied to this method
     * @return the value of {@code now.plusMinutes(preparation.getDeliveryLeadMinutes())}
     * @throws IllegalStateException when the method rejects the request with {@code Delivery
     *     preparation lead minutes cannot be negative.}
     */
    private LocalDateTime cutoff(LocalDateTime now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryPreparationQueue.class, "cutoff(LocalDateTime)");
        try {
            if (preparation.getDeliveryLeadMinutes() < 0)
                throw new IllegalStateException(
                        "Delivery preparation lead minutes cannot be negative.");
            return now.plusMinutes(preparation.getDeliveryLeadMinutes());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryPreparationQueue.class,
                    "cutoff(LocalDateTime)");
        }
    }
}
