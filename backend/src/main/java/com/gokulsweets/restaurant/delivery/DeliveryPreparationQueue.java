package com.gokulsweets.restaurant.delivery;

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

/** Branch-scoped delivery queue, using the same IST rider-window lead as preparation eligibility. */
@Service
@RequiredArgsConstructor
public class DeliveryPreparationQueue {
    private static final String FROM = """
            FROM orders o
            JOIN delivery_capacity_windows w ON w.id = o.delivery_window_id
            JOIN delivery_zones z ON z.id = w.zone_id AND z.branch_id = o.branch_id
            WHERE o.branch_id = ? AND o.fulfillment_type = 'DELIVERY'
              AND o.order_status = 'CONFIRMED'
            """;

    private final JdbcTemplate jdbc;
    private final OrderRepository orders;
    private final PreparationWindowProperties preparation;

    public List<Order> eligible(Long branchId, LocalDateTime now, int limit) {
        LocalDateTime cutoff = cutoff(now);
        List<Long> ids = jdbc.query("SELECT o.id " + FROM + """
                AND (w.service_date, w.starts_at) <= (?, ?)
                ORDER BY w.service_date, w.starts_at, o.created_at, o.id
                LIMIT ?
                """, (rs, row) -> rs.getLong(1), branchId,
                cutoff.toLocalDate(), cutoff.toLocalTime(), limit);
        if (ids.isEmpty()) return List.of();
        Map<Long, Order> byId = orders.findAllById(ids).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(order -> order != null).toList();
    }

    public long confirmed(Long branchId) {
        return jdbc.queryForObject("SELECT COUNT(*) " + FROM, Long.class, branchId);
    }

    public long eligibleCount(Long branchId, LocalDateTime now) {
        LocalDateTime cutoff = cutoff(now);
        return jdbc.queryForObject("SELECT COUNT(*) " + FROM
                + " AND (w.service_date, w.starts_at) <= (?, ?)", Long.class,
                branchId, cutoff.toLocalDate(), cutoff.toLocalTime());
    }

    public long overdueCount(Long branchId, LocalDateTime now) {
        return jdbc.queryForObject("SELECT COUNT(*) " + FROM
                + " AND (w.service_date, w.starts_at) <= (?, ?)", Long.class,
                branchId, now.toLocalDate(), now.toLocalTime());
    }

    private LocalDateTime cutoff(LocalDateTime now) {
        if (preparation.getDeliveryLeadMinutes() < 0)
            throw new IllegalStateException("Delivery preparation lead minutes cannot be negative.");
        return now.plusMinutes(preparation.getDeliveryLeadMinutes());
    }
}
