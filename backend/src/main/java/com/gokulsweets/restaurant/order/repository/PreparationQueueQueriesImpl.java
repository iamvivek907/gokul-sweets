package com.gokulsweets.restaurant.order.repository;

import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Same cutoff and whole-basket policy as the preparation workflow, evaluated by PostgreSQL. */
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PreparationQueueQueriesImpl implements PreparationQueueQueries {
    private final JdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext
    private EntityManager entityManager;
    private static final String SCOPE = """
        FROM orders o JOIN pickup_slots ps ON ps.id=o.pickup_slot_id
        WHERE o.branch_id=? AND o.order_status=? AND (
            (o.pickup_type=? AND (ps.slot_date<? OR (ps.slot_date=? AND ps.start_time<=?)))
            OR (o.pickup_type=? AND (ps.slot_date<? OR (ps.slot_date=? AND ps.start_time<=?)))
            OR (o.pickup_type=? AND (ps.slot_date<? OR (ps.slot_date=? AND ps.start_time<=?)))
            OR (ps.slot_date=? AND EXISTS(SELECT 1 FROM order_items i WHERE i.order_id=o.id)
                AND NOT EXISTS(SELECT 1 FROM order_items i
                    LEFT JOIN branch_products bp ON bp.branch_id=o.branch_id AND bp.product_id=i.product_id
                    WHERE i.order_id=o.id AND NOT COALESCE(bp.early_preparation_allowed,FALSE)))
        )
        """;

    @Override
    public List<Order> findPreparationQueueCandidates(
            Long branchId,
            OrderStatus confirmedStatus,
            PickupType normalType,
            LocalDate normalCutoffDate,
            LocalTime normalCutoffTime,
            PickupType priorityType,
            LocalDate priorityCutoffDate,
            LocalTime priorityCutoffTime,
            PickupType adminOverrideType,
            LocalDate adminOverrideCutoffDate,
            LocalTime adminOverrideCutoffTime,
            LocalDate earlyDate,

            Pageable pageable
    ) {
        var args = parameters(branchId, confirmedStatus, normalType, normalCutoffDate, normalCutoffTime,
            priorityType, priorityCutoffDate, priorityCutoffTime, adminOverrideType,
            adminOverrideCutoffDate, adminOverrideCutoffTime, earlyDate);
        String sql = "SELECT o.id " + SCOPE + " ORDER BY ps.slot_date,ps.start_time,o.created_at,o.id";
        if (pageable.isPaged()) {
            sql += " LIMIT ? OFFSET ?";
            args.add(pageable.getPageSize());
            args.add(pageable.getOffset());
        }
        var ids = jdbc.queryForList(sql, Long.class, args.toArray());
        if (ids.isEmpty()) return List.of();
        // Recheck scope: an order can be transferred or started after the ID query.
        // Fetch relations in one batch while excluding orders that left this queue.
        var rows = entityManager.createQuery(
            "SELECT o FROM Order o JOIN FETCH o.branch JOIN FETCH o.pickupSlot WHERE o.id IN :ids AND o.branch.id=:branchId AND o.orderStatus=:status", Order.class)
            .setParameter("ids", ids).setParameter("branchId", branchId)
            .setParameter("status", confirmedStatus).getResultList();
        var indexed = rows.stream().collect(Collectors.toMap(Order::getId, Function.identity()));
        return ids.stream().map(indexed::get).filter(Objects::nonNull).toList();
    }

    @Override
    public long countPreparationQueueCandidates(
            Long branchId,
            OrderStatus confirmedStatus,
            PickupType normalType,
            LocalDate normalCutoffDate,
            LocalTime normalCutoffTime,
            PickupType priorityType,
            LocalDate priorityCutoffDate,
            LocalTime priorityCutoffTime,
            PickupType adminOverrideType,
            LocalDate adminOverrideCutoffDate,
            LocalTime adminOverrideCutoffTime,
            LocalDate earlyDate
    ) {
        var args = parameters(branchId, confirmedStatus, normalType, normalCutoffDate, normalCutoffTime,
            priorityType, priorityCutoffDate, priorityCutoffTime, adminOverrideType,
            adminOverrideCutoffDate, adminOverrideCutoffTime, earlyDate);
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) " + SCOPE, Long.class, args.toArray()));
    }

    private List<Object> parameters(Long branchId, OrderStatus status,
            PickupType normal, LocalDate normalDate, LocalTime normalTime,
            PickupType priority, LocalDate priorityDate, LocalTime priorityTime,
            PickupType override, LocalDate overrideDate, LocalTime overrideTime, LocalDate earlyDate) {
        return new ArrayList<>(Arrays.asList(branchId, status.name(),
            normal.name(), normalDate, normalDate, normalTime,
            priority.name(), priorityDate, priorityDate, priorityTime,
            override.name(), overrideDate, overrideDate, overrideTime, earlyDate));
    }
}
