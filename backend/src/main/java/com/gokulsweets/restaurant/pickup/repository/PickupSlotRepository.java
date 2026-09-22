package com.gokulsweets.restaurant.pickup.repository;

import com.gokulsweets.restaurant.pickup.PickupSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface PickupSlotRepository extends JpaRepository<PickupSlot, Long> {

    List<PickupSlot> findByBranchIdAndSlotDateAndActiveTrueOrderByStartTimeAsc(
            Long branchId,
            LocalDate slotDate
    );

    boolean existsByBranchIdAndSlotDateAndStartTimeAndEndTimeAndIdNot(
            Long branchId,
            LocalDate slotDate,
            LocalTime startTime,
            LocalTime endTime,
            Long id
    );

    Optional<PickupSlot> findByBranchIdAndSlotDateAndStartTimeAndEndTime(
            Long branchId,
            LocalDate slotDate,
            LocalTime startTime,
            LocalTime endTime
    );

    boolean existsByBranchIdAndSlotDateAndStartTimeLessThanAndEndTimeGreaterThan(
            Long branchId,
            LocalDate slotDate,
            LocalTime endTime,
            LocalTime startTime
    );


    boolean existsByBranchIdAndSlotDateAndStartTimeLessThanAndEndTimeGreaterThanAndIdNot(
            Long branchId,
            LocalDate slotDate,
            LocalTime endTime,
            LocalTime startTime,
            Long id
    );

    @Modifying
    @Query("""
    UPDATE PickupSlot p
    SET p.bookedCount = p.bookedCount + 1
    WHERE p.id = :slotId
      AND p.active = true
      AND p.bookedCount < p.capacity""")
    int reserveNormalCapacity(@Param("slotId") Long slotId);

    @Modifying
    @Query("""
    UPDATE PickupSlot p
    SET p.priorityBookedCount = p.priorityBookedCount + 1
    WHERE p.id = :slotId
      AND p.active = true
      AND p.priorityEnabled = true
      AND p.priorityBookedCount < p.priorityCapacity""")
    int reservePriorityCapacity(@Param("slotId") Long slotId);

    @Modifying
    @Query("""
    UPDATE PickupSlot p
    SET p.bookedCount = p.bookedCount - 1
    WHERE p.id = :slotId
      AND p.bookedCount > 0""")
    int releaseNormalCapacity(@Param("slotId") Long slotId);

    @Modifying
    @Query("""
    UPDATE PickupSlot p
    SET p.priorityBookedCount = p.priorityBookedCount - 1
    WHERE p.id = :slotId
      AND p.priorityBookedCount > 0""")
    int releasePriorityCapacity(@Param("slotId") Long slotId);
}