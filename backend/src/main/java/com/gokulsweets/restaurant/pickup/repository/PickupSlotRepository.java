package com.gokulsweets.restaurant.pickup.repository;

import com.gokulsweets.restaurant.pickup.PickupSlot;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/** Persistence operations for pickup slot records. */
public interface PickupSlotRepository extends JpaRepository<PickupSlot, Long> {

    /**
     * Performs the find by branch id and slot date between order by slot date asc start time asc
     * operation for pickup slot repository.
     *
     * @param branchId the branch id
     * @param startDate the start date
     * @param endDate the end date
     * @return the find by branch id and slot date between order by slot date asc start time asc
     *     result
     */
    @EntityGraph(attributePaths = "branch")
    List<PickupSlot> findByBranchIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
            Long branchId, LocalDate startDate, LocalDate endDate);

    /**
     * Finds with branch by id.
     *
     * @param slotId the slot id
     * @return the find with branch by id result
     */
    @EntityGraph(attributePaths = "branch")
    Optional<PickupSlot> findWithBranchById(Long slotId);

    /**
     * Performs the find by branch id and slot date and active true order by start time asc
     * operation for pickup slot repository.
     *
     * @param branchId the branch id
     * @param slotDate the slot date
     * @return the find by branch id and slot date and active true order by start time asc result
     */
    List<PickupSlot> findByBranchIdAndSlotDateAndActiveTrueOrderByStartTimeAsc(
            Long branchId, LocalDate slotDate);

    /**
     * Performs the exists by branch id and slot date and start time and end time and id not
     * operation for pickup slot repository.
     *
     * @param branchId the branch id
     * @param slotDate the slot date
     * @param startTime the start time
     * @param endTime the end time
     * @param id the id
     * @return the exists by branch id and slot date and start time and end time and id not result
     */
    boolean existsByBranchIdAndSlotDateAndStartTimeAndEndTimeAndIdNot(
            Long branchId, LocalDate slotDate, LocalTime startTime, LocalTime endTime, Long id);

    /**
     * Performs the find by branch id and slot date and start time and end time operation for pickup
     * slot repository.
     *
     * @param branchId the branch id
     * @param slotDate the slot date
     * @param startTime the start time
     * @param endTime the end time
     * @return the find by branch id and slot date and start time and end time result
     */
    Optional<PickupSlot> findByBranchIdAndSlotDateAndStartTimeAndEndTime(
            Long branchId, LocalDate slotDate, LocalTime startTime, LocalTime endTime);

    /**
     * Performs the exists by branch id and slot date and start time less than and end time greater
     * than operation for pickup slot repository.
     *
     * @param branchId the branch id
     * @param slotDate the slot date
     * @param endTime the end time
     * @param startTime the start time
     * @return the exists by branch id and slot date and start time less than and end time greater
     *     than result
     */
    boolean existsByBranchIdAndSlotDateAndStartTimeLessThanAndEndTimeGreaterThan(
            Long branchId, LocalDate slotDate, LocalTime endTime, LocalTime startTime);

    /**
     * Performs the exists by branch id and slot date and start time less than and end time greater
     * than and id not operation for pickup slot repository.
     *
     * @param branchId the branch id
     * @param slotDate the slot date
     * @param endTime the end time
     * @param startTime the start time
     * @param id the id
     * @return the exists by branch id and slot date and start time less than and end time greater
     *     than and id not result
     */
    boolean existsByBranchIdAndSlotDateAndStartTimeLessThanAndEndTimeGreaterThanAndIdNot(
            Long branchId, LocalDate slotDate, LocalTime endTime, LocalTime startTime, Long id);

    /**
     * Reserves normal capacity.
     *
     * @param slotId the slot id
     * @return the reserve normal capacity result
     */
    @Modifying
    @Query(
            """
            UPDATE PickupSlot p
            SET p.bookedCount = p.bookedCount + 1
            WHERE p.id = :slotId
              AND p.active = true
              AND p.bookedCount < p.capacity""")
    int reserveNormalCapacity(@Param("slotId") Long slotId);

    /**
     * Reserves priority capacity.
     *
     * @param slotId the slot id
     * @return the reserve priority capacity result
     */
    @Modifying
    @Query(
            """
            UPDATE PickupSlot p
            SET p.priorityBookedCount = p.priorityBookedCount + 1
            WHERE p.id = :slotId
              AND p.active = true
              AND p.priorityEnabled = true
              AND p.priorityBookedCount < p.priorityCapacity""")
    int reservePriorityCapacity(@Param("slotId") Long slotId);

    /**
     * Releases normal capacity.
     *
     * @param slotId the slot id
     * @return the release normal capacity result
     */
    @Modifying
    @Query(
            """
            UPDATE PickupSlot p
            SET p.bookedCount = p.bookedCount - 1
            WHERE p.id = :slotId
              AND p.bookedCount > 0""")
    int releaseNormalCapacity(@Param("slotId") Long slotId);

    /**
     * Releases priority capacity.
     *
     * @param slotId the slot id
     * @return the release priority capacity result
     */
    @Modifying
    @Query(
            """
            UPDATE PickupSlot p
            SET p.priorityBookedCount = p.priorityBookedCount - 1
            WHERE p.id = :slotId
              AND p.priorityBookedCount > 0""")
    int releasePriorityCapacity(@Param("slotId") Long slotId);
}
