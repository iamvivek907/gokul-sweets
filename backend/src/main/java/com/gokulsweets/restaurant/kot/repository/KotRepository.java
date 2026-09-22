package com.gokulsweets.restaurant.kot.repository;

import com.gokulsweets.restaurant.kot.entity.Kot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface KotRepository
        extends JpaRepository<Kot, Long> {

    boolean existsByOrderId(
            Long orderId
    );


    Optional<Kot> findByOrderId(
            Long orderId
    );


    Optional<Kot> findByKotNumber(
            String kotNumber
    );


    @EntityGraph(
            attributePaths = {
                    "order",
                    "order.pickupSlot",
                    "branch",
                    "items",
                    "items.product"
            }
    )
    @Query("""
            SELECT DISTINCT k
            FROM Kot k
            WHERE k.kotNumber = :kotNumber
            """)
    Optional<Kot> findDetailedByKotNumber(
            @Param("kotNumber")
            String kotNumber
    );


    @EntityGraph(
            attributePaths = {
                    "order",
                    "order.pickupSlot",
                    "branch",
                    "items",
                    "items.product"
            }
    )
    @Query("""
            SELECT DISTINCT k
            FROM Kot k
            WHERE k.order.id = :orderId
            """)
    Optional<Kot> findDetailedByOrderId(
            @Param("orderId")
            Long orderId
    );


    @EntityGraph(
            attributePaths = {
                    "order",
                    "order.pickupSlot",
                    "branch",
                    "items",
                    "items.product"
            }
    )
    @Query("""
            SELECT DISTINCT k
            FROM Kot k
            WHERE k.order.orderNumber = :orderNumber
            """)
    Optional<Kot> findDetailedByOrderNumber(
            @Param("orderNumber")
            String orderNumber
    );


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

    @Lock(
            LockModeType.PESSIMISTIC_WRITE
    )
    @EntityGraph(
            attributePaths = {
                    "order",
                    "order.pickupSlot",
                    "branch",
                    "items",
                    "items.product"
            }
    )
    @Query("""
            SELECT DISTINCT k
            FROM Kot k
            WHERE k.kotNumber = :kotNumber
            """)
    Optional<Kot> findForPrintByKotNumber(
            @Param("kotNumber")
            String kotNumber
    );
}