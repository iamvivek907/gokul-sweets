package com.gokulsweets.restaurant.kot.repository;

import com.gokulsweets.restaurant.kot.entity.Kot;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Persistence operations for kot records. */
public interface KotRepository extends JpaRepository<Kot, Long> {

    /**
     * Existses by order id.
     *
     * @param orderId the order id
     * @return the exists by order id result
     */
    boolean existsByOrderId(Long orderId);

    /**
     * Finds by order id.
     *
     * @param orderId the order id
     * @return the find by order id result
     */
    Optional<Kot> findByOrderId(Long orderId);

    /**
     * Finds by kot number.
     *
     * @param kotNumber the kot number
     * @return the find by kot number result
     */
    Optional<Kot> findByKotNumber(String kotNumber);

    /**
     * Finds detailed by kot number.
     *
     * @param kotNumber the kot number
     * @return the find detailed by kot number result
     */
    @EntityGraph(attributePaths = {"order", "order.pickupSlot", "branch", "items", "items.product"})
    @Query(
            """
            SELECT DISTINCT k
            FROM Kot k
            WHERE k.kotNumber = :kotNumber
            """)
    Optional<Kot> findDetailedByKotNumber(@Param("kotNumber") String kotNumber);

    /**
     * Finds detailed by order id.
     *
     * @param orderId the order id
     * @return the find detailed by order id result
     */
    @EntityGraph(attributePaths = {"order", "order.pickupSlot", "branch", "items", "items.product"})
    @Query(
            """
            SELECT DISTINCT k
            FROM Kot k
            WHERE k.order.id = :orderId
            """)
    Optional<Kot> findDetailedByOrderId(@Param("orderId") Long orderId);

    /**
     * Finds detailed by order number.
     *
     * @param orderNumber the order number
     * @return the find detailed by order number result
     */
    @EntityGraph(attributePaths = {"order", "order.pickupSlot", "branch", "items", "items.product"})
    @Query(
            """
            SELECT DISTINCT k
            FROM Kot k
            WHERE k.order.orderNumber = :orderNumber
            """)
    Optional<Kot> findDetailedByOrderNumber(@Param("orderNumber") String orderNumber);

    /*
     * =========================================================
     * PRINT AUDIT LOCK
     * =========================================================
     *
     * Multiple admin devices may attempt to print the same KOT
     * at nearly the same time.
     *
     * The pessimistic write lock prevents lost print-count
     * updates and preserves the correct first-print identity.
     */
    /**
     * Finds for print by kot number.
     *
     * @param kotNumber the kot number
     * @return the find for print by kot number result
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"order", "order.pickupSlot", "branch", "items", "items.product"})
    @Query(
            """
            SELECT DISTINCT k
            FROM Kot k
            WHERE k.kotNumber = :kotNumber
            """)
    Optional<Kot> findForPrintByKotNumber(@Param("kotNumber") String kotNumber);
}
