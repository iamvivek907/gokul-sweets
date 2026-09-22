package com.gokulsweets.restaurant.order.lifecycle.repository;

import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface PickupLifecycleOrderRepository
        extends JpaRepository<Order, Long> {

    @Query("""
            SELECT o.orderNumber
            FROM Order o
            JOIN o.pickupSlot slot
            WHERE o.orderStatus = :status
              AND (
                    slot.slotDate < :cutoffDate
                    OR (
                        slot.slotDate = :cutoffDate
                        AND slot.endTime <= :cutoffTime
                    )
              )
            ORDER BY slot.slotDate ASC, slot.endTime ASC, o.id ASC
            """)
    List<String> findDueOrderNumbers(
            @Param("status") OrderStatus status,
            @Param("cutoffDate") LocalDate cutoffDate,
            @Param("cutoffTime") LocalTime cutoffTime,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"branch", "pickupSlot"})
    @Query("SELECT o FROM Order o WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberForUpdate(
            @Param("orderNumber") String orderNumber
    );
}
