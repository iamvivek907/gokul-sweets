package com.gokulsweets.restaurant.order.repository;

import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository
        extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(
            String orderNumber
    );


    /*
     * Lightweight customer history lookup.
     *
     * The browser supplies only order numbers it already owns.
     * Branch and pickup slot are fetched in the same query so
     * mapping hundreds of summary rows does not cause N+1 reads.
     */
    @EntityGraph(
            attributePaths = {
                    "branch",
                    "pickupSlot"
            }
    )
    List<Order> findByOrderNumberInOrderByCreatedAtDesc(
            List<String> orderNumbers
    );


    boolean existsByOrderNumber(
            String orderNumber
    );


    @EntityGraph(
            attributePaths = {
                    "branch",
                    "pickupSlot"
            }
    )
    Page<Order> findByBranchIdAndOrderStatus(
            Long branchId,
            OrderStatus orderStatus,
            Pageable pageable
    );


    @EntityGraph(
            attributePaths = {
                    "branch",
                    "pickupSlot"
            }
    )
    Page<Order> findByBranchId(
            Long branchId,
            Pageable pageable
    );


    @EntityGraph(
            attributePaths = {
                    "branch",
                    "pickupSlot"
            }
    )
    Page<Order> findByCustomerContactIdOrderByCreatedAtDesc(
            Long customerContactId,
            Pageable pageable
    );


    /*
     * =========================================================
     * STATUS COUNTS
     * =========================================================
     */

    long countByBranchIdAndOrderStatus(
            Long branchId,
            OrderStatus orderStatus
    );


    /*
     * =========================================================
     * OPERATIONAL PREPARATION QUEUE
     * =========================================================
     */

    @Query("""
            SELECT o
            FROM Order o
            JOIN FETCH o.branch b
            JOIN FETCH o.pickupSlot ps
            WHERE b.id = :branchId
              AND o.orderStatus = :confirmedStatus
              AND (
                    (
                        o.pickupType = :normalType
                        AND (
                            ps.slotDate < :normalCutoffDate
                            OR (
                                ps.slotDate = :normalCutoffDate
                                AND ps.startTime <= :normalCutoffTime
                            )
                        )
                    )
                    OR
                    (
                        o.pickupType = :priorityType
                        AND (
                            ps.slotDate < :priorityCutoffDate
                            OR (
                                ps.slotDate = :priorityCutoffDate
                                AND ps.startTime <= :priorityCutoffTime
                            )
                        )
                    )
                    OR
                    (
                        o.pickupType = :adminOverrideType
                        AND (
                            ps.slotDate < :adminOverrideCutoffDate
                            OR (
                                ps.slotDate = :adminOverrideCutoffDate
                                AND ps.startTime <= :adminOverrideCutoffTime
                            )
                        )
                    )
              )
            ORDER BY
                ps.slotDate ASC,
                ps.startTime ASC,
                o.createdAt ASC,
                o.id ASC
            """)
    List<Order> findPreparationQueueCandidates(

            @Param("branchId")
            Long branchId,

            @Param("confirmedStatus")
            OrderStatus confirmedStatus,

            @Param("normalType")
            PickupType normalType,

            @Param("normalCutoffDate")
            LocalDate normalCutoffDate,

            @Param("normalCutoffTime")
            LocalTime normalCutoffTime,

            @Param("priorityType")
            PickupType priorityType,

            @Param("priorityCutoffDate")
            LocalDate priorityCutoffDate,

            @Param("priorityCutoffTime")
            LocalTime priorityCutoffTime,

            @Param("adminOverrideType")
            PickupType adminOverrideType,

            @Param("adminOverrideCutoffDate")
            LocalDate adminOverrideCutoffDate,

            @Param("adminOverrideCutoffTime")
            LocalTime adminOverrideCutoffTime,

            Pageable pageable
    );


    @Query("""
            SELECT COUNT(o)
            FROM Order o
            JOIN o.branch b
            JOIN o.pickupSlot ps
            WHERE b.id = :branchId
              AND o.orderStatus = :confirmedStatus
              AND (
                    (
                        o.pickupType = :normalType
                        AND (
                            ps.slotDate < :normalCutoffDate
                            OR (
                                ps.slotDate = :normalCutoffDate
                                AND ps.startTime <= :normalCutoffTime
                            )
                        )
                    )
                    OR
                    (
                        o.pickupType = :priorityType
                        AND (
                            ps.slotDate < :priorityCutoffDate
                            OR (
                                ps.slotDate = :priorityCutoffDate
                                AND ps.startTime <= :priorityCutoffTime
                            )
                        )
                    )
                    OR
                    (
                        o.pickupType = :adminOverrideType
                        AND (
                            ps.slotDate < :adminOverrideCutoffDate
                            OR (
                                ps.slotDate = :adminOverrideCutoffDate
                                AND ps.startTime <= :adminOverrideCutoffTime
                            )
                        )
                    )
              )
            """)
    long countPreparationQueueCandidates(

            @Param("branchId")
            Long branchId,

            @Param("confirmedStatus")
            OrderStatus confirmedStatus,

            @Param("normalType")
            PickupType normalType,

            @Param("normalCutoffDate")
            LocalDate normalCutoffDate,

            @Param("normalCutoffTime")
            LocalTime normalCutoffTime,

            @Param("priorityType")
            PickupType priorityType,

            @Param("priorityCutoffDate")
            LocalDate priorityCutoffDate,

            @Param("priorityCutoffTime")
            LocalTime priorityCutoffTime,

            @Param("adminOverrideType")
            PickupType adminOverrideType,

            @Param("adminOverrideCutoffDate")
            LocalDate adminOverrideCutoffDate,

            @Param("adminOverrideCutoffTime")
            LocalTime adminOverrideCutoffTime
    );


    @Query("""
            SELECT COUNT(o)
            FROM Order o
            JOIN o.branch b
            JOIN o.pickupSlot ps
            WHERE b.id = :branchId
              AND o.orderStatus = :confirmedStatus
              AND (
                    ps.slotDate < :currentDate
                    OR (
                        ps.slotDate = :currentDate
                        AND ps.startTime <= :currentTime
                    )
              )
            """)
    long countOverdueConfirmedOrders(

            @Param("branchId")
            Long branchId,

            @Param("confirmedStatus")
            OrderStatus confirmedStatus,

            @Param("currentDate")
            LocalDate currentDate,

            @Param("currentTime")
            LocalTime currentTime
    );


    @EntityGraph(
            attributePaths = {
                    "branch",
                    "pickupSlot",
                    "items",
                    "items.product",
                    "rebate"
            }
    )
    @Query("""
            SELECT DISTINCT o
            FROM Order o
            WHERE o.orderNumber = :orderNumber
            """)
    Optional<Order> findDetailedByOrderNumber(
            @Param("orderNumber")
            String orderNumber
    );


    List<Order>
    findTop100ByOrderStatusAndReservationExpiresAtBeforeOrderByReservationExpiresAtAsc(
            OrderStatus orderStatus,
            LocalDateTime reservationExpiresAt
    );


    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query("""
            UPDATE Order o
            SET o.orderStatus = :newStatus,
                o.updatedAt = CURRENT_TIMESTAMP
            WHERE o.id = :orderId
              AND o.orderStatus = :expectedStatus
            """)
    int transitionStatus(
            @Param("orderId")
            Long orderId,

            @Param("expectedStatus")
            OrderStatus expectedStatus,

            @Param("newStatus")
            OrderStatus newStatus
    );


    @Lock(
            LockModeType.PESSIMISTIC_WRITE
    )
    @EntityGraph(
            attributePaths = {
                    "branch",
                    "pickupSlot",
                    "rebate"
            }
    )
    @Query("""
            SELECT o
            FROM Order o
            WHERE o.orderNumber = :orderNumber
            """)
    Optional<Order> findForUpdate(
            @Param("orderNumber")
            String orderNumber
    );
}
