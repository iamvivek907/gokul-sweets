package com.gokulsweets.restaurant.order.repository;

import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.enums.OrderStatus;

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

/** Persistence operations for order records. */
public interface OrderRepository extends JpaRepository<Order, Long>, PreparationQueueQueries {

    /**
     * Finds by customer order number.
     *
     * @param customerOrderNumber the customer order number
     * @return the find by customer order number result
     */
    Optional<Order> findByCustomerOrderNumber(Long customerOrderNumber);

    /**
     * Cancels failed checkout.
     *
     * @param id the id
     * @return the cancel failed checkout result
     */
    @org.springframework.data.jpa.repository.Modifying(
            clearAutomatically = true,
            flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query(
            "UPDATE Order o SET"
                + " o.orderStatus=com.gokulsweets.restaurant.order.enums.OrderStatus.CANCELLED"
                + " WHERE o.id=:id AND"
                + " o.orderStatus=com.gokulsweets.restaurant.order.enums.OrderStatus.PAYMENT_FAILED")
    int cancelFailedCheckout(@org.springframework.data.repository.query.Param("id") Long id);

    /**
     * Finds by order number.
     *
     * @param orderNumber the order number
     * @return the find by order number result
     */
    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * Finds by order number in order by created at desc.
     *
     * @param orderNumbers the order numbers
     * @return the find by order number in order by created at desc result
     */
    @EntityGraph(attributePaths = {"branch", "pickupSlot"})
    List<Order> findByOrderNumberInOrderByCreatedAtDesc(List<String> orderNumbers);

    /**
     * Existses by order number.
     *
     * @param orderNumber the order number
     * @return the exists by order number result
     */
    boolean existsByOrderNumber(String orderNumber);

    /**
     * Finds by branch id and order status.
     *
     * @param branchId the branch id
     * @param orderStatus the order status
     * @param pageable the pageable
     * @return the find by branch id and order status result
     */
    @EntityGraph(attributePaths = {"branch", "pickupSlot"})
    Page<Order> findByBranchIdAndOrderStatus(
            Long branchId, OrderStatus orderStatus, Pageable pageable);

    /**
     * Finds by branch id.
     *
     * @param branchId the branch id
     * @param pageable the pageable
     * @return the find by branch id result
     */
    @EntityGraph(attributePaths = {"branch", "pickupSlot"})
    Page<Order> findByBranchId(Long branchId, Pageable pageable);

    /**
     * Performs the find by customer contact id order by created at desc operation for order
     * repository.
     *
     * @param customerContactId the customer contact id
     * @param pageable the pageable
     * @return the find by customer contact id order by created at desc result
     */
    @EntityGraph(attributePaths = {"branch", "pickupSlot"})
    Page<Order> findByCustomerContactIdOrderByCreatedAtDesc(
            Long customerContactId, Pageable pageable);

    /*
     * =========================================================
     * STATUS COUNTS
     * =========================================================
     */
    /**
     * Counts by branch id and order status.
     *
     * @param branchId the branch id
     * @param orderStatus the order status
     * @return the count by branch id and order status result
     */
    long countByBranchIdAndOrderStatus(Long branchId, OrderStatus orderStatus);

    /**
     * Performs the count by branch id and order status and fulfillment type operation for order
     * repository.
     *
     * @param branchId the branch id
     * @param orderStatus the order status
     * @param fulfillmentType the fulfillment type
     * @return the count by branch id and order status and fulfillment type result
     */
    long countByBranchIdAndOrderStatusAndFulfillmentType(
            Long branchId, OrderStatus orderStatus, FulfillmentType fulfillmentType);

    /*
     * =========================================================
     * OPERATIONAL PREPARATION QUEUE
     * =========================================================
     */
    /**
     * Counts overdue confirmed orders.
     *
     * @param branchId the branch id
     * @param confirmedStatus the confirmed status
     * @param currentDate the current date
     * @param currentTime the current time
     * @return the count overdue confirmed orders result
     */
    @Query(
            """
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
            @Param("branchId") Long branchId,
            @Param("confirmedStatus") OrderStatus confirmedStatus,
            @Param("currentDate") LocalDate currentDate,
            @Param("currentTime") LocalTime currentTime);

    /**
     * Finds detailed by order number.
     *
     * @param orderNumber the order number
     * @return the find detailed by order number result
     */
    @EntityGraph(attributePaths = {"branch", "pickupSlot", "items", "items.product", "rebate"})
    @Query(
            """
            SELECT DISTINCT o
            FROM Order o
            WHERE o.orderNumber = :orderNumber
            """)
    Optional<Order> findDetailedByOrderNumber(@Param("orderNumber") String orderNumber);

    /**
     * Performs the find top100 by order status and reservation expires at before order by
     * reservation expires at asc operation for order repository.
     *
     * @param orderStatus the order status
     * @param reservationExpiresAt the reservation expires at
     * @return the find top100 by order status and reservation expires at before order by
     *     reservation expires at asc result
     */
    List<Order> findTop100ByOrderStatusAndReservationExpiresAtBeforeOrderByReservationExpiresAtAsc(
            OrderStatus orderStatus, LocalDateTime reservationExpiresAt);

    /**
     * Transitions status.
     *
     * @param orderId the order id
     * @param expectedStatus the expected status
     * @param newStatus the new status
     * @return the transition status result
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            UPDATE Order o
            SET o.orderStatus = :newStatus,
                o.updatedAt = CURRENT_TIMESTAMP
            WHERE o.id = :orderId
              AND o.orderStatus = :expectedStatus
            """)
    int transitionStatus(
            @Param("orderId") Long orderId,
            @Param("expectedStatus") OrderStatus expectedStatus,
            @Param("newStatus") OrderStatus newStatus);

    /**
     * Finds for update.
     *
     * @param orderNumber the order number
     * @return the find for update result
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"branch", "pickupSlot", "rebate"})
    @Query(
            """
            SELECT o
            FROM Order o
            WHERE o.orderNumber = :orderNumber
            """)
    Optional<Order> findForUpdate(@Param("orderNumber") String orderNumber);
}
