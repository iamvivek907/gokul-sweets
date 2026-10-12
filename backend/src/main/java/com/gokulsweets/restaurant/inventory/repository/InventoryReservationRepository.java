package com.gokulsweets.restaurant.inventory.repository;

import com.gokulsweets.restaurant.inventory.entity.InventoryReservation;
import com.gokulsweets.restaurant.inventory.enums.InventoryReservationStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Persistence operations for inventory reservation records. */
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    /**
     * Finds by reservation key.
     *
     * @param reservationKey the reservation key
     * @return the find by reservation key result
     */
    Optional<InventoryReservation> findByReservationKey(String reservationKey);

    /**
     * Performs the find by reservation key for update operation for inventory reservation
     * repository.
     *
     * @param reservationKey the reservation key
     * @return the find by reservation key for update result
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT reservation
            FROM InventoryReservation reservation
            JOIN FETCH reservation.allocation allocation
            JOIN FETCH allocation.branchProduct branchProduct
            WHERE reservation.reservationKey = :reservationKey
            """)
    Optional<InventoryReservation> findByReservationKeyForUpdate(
            @Param("reservationKey") String reservationKey);

    /**
     * Finds by order number for update.
     *
     * @param orderNumber the order number
     * @return the find by order number for update result
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT DISTINCT reservation
            FROM InventoryReservation reservation
            JOIN FETCH reservation.allocation allocation
            JOIN FETCH allocation.branchProduct branchProduct
            WHERE reservation.orderNumber = :orderNumber
            ORDER BY reservation.id ASC
            """)
    List<InventoryReservation> findByOrderNumberForUpdate(@Param("orderNumber") String orderNumber);

    /**
     * Finds expired hold keys.
     *
     * @param status the status
     * @param now the now
     * @param pageable the pageable
     * @return the find expired hold keys result
     */
    @Query(
            """
            SELECT reservation.reservationKey
            FROM InventoryReservation reservation
            WHERE reservation.status = :status
              AND reservation.orderNumber IS NULL
              AND reservation.expiresAt <= :now
            ORDER BY reservation.expiresAt ASC
            """)
    List<String> findExpiredHoldKeys(
            @Param("status") InventoryReservationStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);
}
