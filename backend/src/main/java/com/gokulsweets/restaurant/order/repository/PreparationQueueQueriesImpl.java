package com.gokulsweets.restaurant.order.repository;

import com.gokulsweets.restaurant.observability.MethodTiming;
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

    @jakarta.persistence.PersistenceContext private EntityManager entityManager;

    private static final String SCOPE = AppConstant.PREPARATION_QUEUE_QUERIES_IMPL_SCOPE;

    /**
     * Finds preparation queue candidates.
     *
     * @param branchId the branch id
     * @param confirmedStatus the confirmed status
     * @param normalType the normal type
     * @param normalCutoffDate the normal cutoff date
     * @param normalCutoffTime the normal cutoff time
     * @param priorityType the priority type
     * @param priorityCutoffDate the priority cutoff date
     * @param priorityCutoffTime the priority cutoff time
     * @param adminOverrideType the admin override type
     * @param adminOverrideCutoffDate the admin override cutoff date
     * @param adminOverrideCutoffTime the admin override cutoff time
     * @param earlyDate the early date
     * @param pageable the pageable
     * @return the find preparation queue candidates result
     */
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
            Pageable pageable) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PreparationQueueQueriesImpl.class,
                        "findPreparationQueueCandidates(Long,OrderStatus,PickupType,LocalDate,LocalTime,PickupType,LocalDate,LocalTime,PickupType,LocalDate,LocalTime,LocalDate,Pageable)");
        try {
            var args =
                    parameters(
                            branchId,
                            confirmedStatus,
                            normalType,
                            normalCutoffDate,
                            normalCutoffTime,
                            priorityType,
                            priorityCutoffDate,
                            priorityCutoffTime,
                            adminOverrideType,
                            adminOverrideCutoffDate,
                            adminOverrideCutoffTime,
                            earlyDate);
            String sql =
                    "SELECT o.id "
                            + SCOPE
                            + " ORDER BY ps.slot_date,ps.start_time,o.created_at,o.id";
            if (pageable.isPaged()) {
                sql += " LIMIT ? OFFSET ?";
                args.add(pageable.getPageSize());
                args.add(pageable.getOffset());
            }
            var ids = jdbc.queryForList(sql, Long.class, args.toArray());
            if (ids.isEmpty()) return List.of();
            // Recheck scope: an order can be transferred or started after the ID query.
            // Fetch relations in one batch while excluding orders that left this queue.
            var rows =
                    entityManager
                            .createQuery(
                                    "SELECT o FROM Order o JOIN FETCH o.branch JOIN FETCH"
                                            + " o.pickupSlot WHERE o.id IN :ids AND"
                                            + " o.branch.id=:branchId AND o.orderStatus=:status",
                                    Order.class)
                            .setParameter("ids", ids)
                            .setParameter("branchId", branchId)
                            .setParameter("status", confirmedStatus)
                            .getResultList();
            var indexed =
                    rows.stream().collect(Collectors.toMap(Order::getId, Function.identity()));
            return ids.stream().map(indexed::get).filter(Objects::nonNull).toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PreparationQueueQueriesImpl.class,
                    "findPreparationQueueCandidates(Long,OrderStatus,PickupType,LocalDate,LocalTime,PickupType,LocalDate,LocalTime,PickupType,LocalDate,LocalTime,LocalDate,Pageable)");
        }
    }

    /**
     * Counts preparation queue candidates.
     *
     * @param branchId the branch id
     * @param confirmedStatus the confirmed status
     * @param normalType the normal type
     * @param normalCutoffDate the normal cutoff date
     * @param normalCutoffTime the normal cutoff time
     * @param priorityType the priority type
     * @param priorityCutoffDate the priority cutoff date
     * @param priorityCutoffTime the priority cutoff time
     * @param adminOverrideType the admin override type
     * @param adminOverrideCutoffDate the admin override cutoff date
     * @param adminOverrideCutoffTime the admin override cutoff time
     * @param earlyDate the early date
     * @return the count preparation queue candidates result
     */
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
            LocalDate earlyDate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PreparationQueueQueriesImpl.class,
                        "countPreparationQueueCandidates(Long,OrderStatus,PickupType,LocalDate,LocalTime,PickupType,LocalDate,LocalTime,PickupType,LocalDate,LocalTime,LocalDate)");
        try {
            var args =
                    parameters(
                            branchId,
                            confirmedStatus,
                            normalType,
                            normalCutoffDate,
                            normalCutoffTime,
                            priorityType,
                            priorityCutoffDate,
                            priorityCutoffTime,
                            adminOverrideType,
                            adminOverrideCutoffDate,
                            adminOverrideCutoffTime,
                            earlyDate);
            return Objects.requireNonNull(
                    jdbc.queryForObject("SELECT COUNT(*) " + SCOPE, Long.class, args.toArray()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PreparationQueueQueriesImpl.class,
                    "countPreparationQueueCandidates(Long,OrderStatus,PickupType,LocalDate,LocalTime,PickupType,LocalDate,LocalTime,PickupType,LocalDate,LocalTime,LocalDate)");
        }
    }

    /**
     * Parameterses the operation.
     *
     * @param branchId the branch id
     * @param status the status
     * @param normal the normal
     * @param normalDate the normal date
     * @param normalTime the normal time
     * @param priority the priority
     * @param priorityDate the priority date
     * @param priorityTime the priority time
     * @param override the override
     * @param overrideDate the override date
     * @param overrideTime the override time
     * @param earlyDate the early date
     * @return the parameters result
     */
    private List<Object> parameters(
            Long branchId,
            OrderStatus status,
            PickupType normal,
            LocalDate normalDate,
            LocalTime normalTime,
            PickupType priority,
            LocalDate priorityDate,
            LocalTime priorityTime,
            PickupType override,
            LocalDate overrideDate,
            LocalTime overrideTime,
            LocalDate earlyDate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PreparationQueueQueriesImpl.class,
                        "parameters(Long,OrderStatus,PickupType,LocalDate,LocalTime,PickupType,LocalDate,LocalTime,PickupType,LocalDate,LocalTime,LocalDate)");
        try {
            return new ArrayList<>(
                    Arrays.asList(
                            branchId,
                            status.name(),
                            normal.name(),
                            normalDate,
                            normalDate,
                            normalTime,
                            priority.name(),
                            priorityDate,
                            priorityDate,
                            priorityTime,
                            override.name(),
                            overrideDate,
                            overrideDate,
                            overrideTime,
                            earlyDate));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PreparationQueueQueriesImpl.class,
                    "parameters(Long,OrderStatus,PickupType,LocalDate,LocalTime,PickupType,LocalDate,LocalTime,PickupType,LocalDate,LocalTime,LocalDate)");
        }
    }
}
