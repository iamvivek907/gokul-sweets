package com.gokulsweets.restaurant.pickup;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.pickup.dto.CreatePickupSlotRequest;
import com.gokulsweets.restaurant.pickup.dto.PickupSlotResponse;
import com.gokulsweets.restaurant.pickup.dto.UpdatePickupSlotRequest;
import com.gokulsweets.restaurant.pickup.repository.PickupSlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;


@Service
public class PickupSlotService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    PickupSlotService.class
            );


    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Kolkata");


    private final PickupSlotRepository pickupSlotRepository;
    private final BranchRepository branchRepository;
    private final PickupSlotValidationService pickupSlotValidationService;


    public PickupSlotService(
            PickupSlotRepository pickupSlotRepository,
            BranchRepository branchRepository,
            PickupSlotValidationService pickupSlotValidationService
    ) {

        this.pickupSlotRepository =
                pickupSlotRepository;

        this.branchRepository =
                branchRepository;

        this.pickupSlotValidationService =
                pickupSlotValidationService;
    }


    /*
     * =========================================================
     * CUSTOMER - GET AVAILABLE PICKUP SLOTS
     * =========================================================
     */
    @Transactional(readOnly = true)
    public List<PickupSlotResponse> getAvailableSlots(
            Long branchId,
            LocalDate date
    ) {
        LocalDate today =
                LocalDate.now(
                        BUSINESS_ZONE
                );

        if (
                date.isBefore(today)
        ) {
            throw new IllegalArgumentException(
                    "This pickup date has already passed."
            );
        }

        log.debug(
                "Fetching pickup slots: branchId={}, date={}",
                branchId,
                date
        );


        List<PickupSlotResponse> slots =
                pickupSlotRepository
                        .findByBranchIdAndSlotDateAndActiveTrueOrderByStartTimeAsc(
                                branchId,
                                date
                        )
                        .stream()

                        /*
                         * Do not expose past pickup windows.
                         */
                        .filter(
                                this::isBookableTime
                        )

                        /*
                         * Slot remains visible if either:
                         *
                         * normal capacity exists
                         *
                         * OR
                         *
                         * priority capacity exists.
                         */
                        .filter(
                                slot ->
                                        slot.getBookedCount()
                                                < slot.getCapacity()
                                                ||
                                                (
                                                        slot.isPriorityEnabled()
                                                                &&
                                                                slot.getPriorityBookedCount()
                                                                        < slot.getPriorityCapacity()
                                                )
                        )

                        .map(
                                PickupSlotResponse::from
                        )

                        .toList();


        log.debug(
                "Found {} available pickup slots: branchId={}, date={}",
                slots.size(),
                branchId,
                date
        );


        return slots;
    }


    /*
     * =========================================================
     * CUSTOMER - CHECK WHETHER SLOT IS STILL BOOKABLE
     * =========================================================
     */
    private boolean isBookableTime(
            PickupSlot slot
    ) {

        LocalDate today =
                LocalDate.now(
                        BUSINESS_ZONE
                );


        LocalTime now =
                LocalTime.now(
                        BUSINESS_ZONE
                );


        /*
         * Past date.
         */
        if (
                slot.getSlotDate()
                        .isBefore(today)
        ) {

            return false;
        }


        /*
         * Future date.
         */
        if (
                slot.getSlotDate()
                        .isAfter(today)
        ) {

            return true;
        }


        /*
         * Today:
         *
         * Once the slot start time has arrived,
         * do not allow new bookings.
         */
        return slot.getStartTime()
                .isAfter(now);
    }


    /*
     * =========================================================
     * ADMIN - BULK CREATE PICKUP SLOTS
     * =========================================================
     *
     * Example request:
     *
     * startDate             2026-09-20
     * endDate               2026-09-22
     * startTime             09:00
     * endTime               12:00
     * slotDurationMinutes   30
     *
     * Generates:
     *
     * 09:00 - 09:30
     * 09:30 - 10:00
     * 10:00 - 10:30
     * ...
     *
     * for every date in the requested range.
     */
    @Transactional
    public List<PickupSlotResponse> createSlots(
            Long branchId,
            CreatePickupSlotRequest request
    ) {

        log.info(
                "Creating pickup slots: branchId={}, startDate={}, endDate={}, " +
                        "startTime={}, endTime={}, slotDurationMinutes={}, capacity={}",
                branchId,
                request.startDate(),
                request.endDate(),
                request.startTime(),
                request.endTime(),
                request.slotDurationMinutes(),
                request.capacity()
        );


        /*
         * =====================================================
         * VALIDATE DATE/TIME RANGE
         * =====================================================
         */
        validateCreateRequest(
                request
        );


        /*
         * =====================================================
         * VALIDATE PRIORITY CONFIGURATION
         * =====================================================
         */
        validatePriorityInput(
                branchId,
                request.priorityEnabled(),
                request.priorityCapacity(),
                request.priorityCharge()
        );


        /*
         * =====================================================
         * FIND BRANCH
         * =====================================================
         */
        Branch branch =
                branchRepository
                        .findById(
                                branchId
                        )
                        .orElseThrow(
                                () -> {

                                    log.warn(
                                            "Cannot create pickup slots. " +
                                                    "Branch not found: branchId={}",
                                            branchId
                                    );


                                    return new IllegalArgumentException(
                                            "Branch not found: "
                                                    + branchId
                                    );
                                }
                        );


        /*
         * =====================================================
         * PRIORITY CONFIGURATION
         * =====================================================
         */
        boolean priorityEnabled =
                Boolean.TRUE.equals(
                        request.priorityEnabled()
                );


        int priorityCapacity =
                request.priorityCapacity() != null
                        ? request.priorityCapacity()
                        : 0;


        BigDecimal priorityCharge =
                request.priorityCharge() != null
                        ? request.priorityCharge()
                        : BigDecimal.ZERO;


        /*
         * If priority is disabled, ignore any
         * priority capacity/charge supplied.
         */
        if (!priorityEnabled) {

            priorityCapacity =
                    0;

            priorityCharge =
                    BigDecimal.ZERO;
        }


        List<PickupSlot> slotsToCreate =
                new ArrayList<>();


        LocalDate today =
                LocalDate.now(
                        BUSINESS_ZONE
                );


        LocalTime now =
                LocalTime.now(
                        BUSINESS_ZONE
                );


        /*
         * =====================================================
         * LOOP THROUGH EACH DATE
         * =====================================================
         */
        LocalDate currentDate =
                request.startDate();


        while (
                !currentDate.isAfter(
                        request.endDate()
                )
        ) {

            LocalTime currentStartTime =
                    request.startTime();


            /*
             * =================================================
             * CREATE TIME WINDOWS FOR THIS DATE
             * =================================================
             */
            while (
                    currentStartTime.isBefore(
                            request.endTime()
                    )
            ) {

                LocalTime currentEndTime =
                        currentStartTime.plusMinutes(
                                request.slotDurationMinutes()
                        );


                /*
                 * Defensive check.
                 *
                 * Validation already guarantees divisibility,
                 * but never allow a generated slot to exceed
                 * requested endTime.
                 */
                if (
                        currentEndTime.isAfter(
                                request.endTime()
                        )
                ) {

                    break;
                }


                /*
                 * If generating slots for today, skip slots
                 * whose start time has already passed.
                 */
                boolean alreadyPassedToday =
                        currentDate.isEqual(
                                today
                        )
                                &&
                                !currentStartTime.isAfter(
                                        now
                                );


                if (alreadyPassedToday) {

                    log.debug(
                            "Skipping passed pickup slot: " +
                                    "branchId={}, date={}, startTime={}, endTime={}",
                            branchId,
                            currentDate,
                            currentStartTime,
                            currentEndTime
                    );


                    currentStartTime =
                            currentEndTime;

                    continue;
                }


                /*
                 * =================================================
                 * CHECK DATABASE FOR OVERLAP
                 * =================================================
                 *
                 * Existing:
                 * 10:00 - 10:30
                 *
                 * These overlap:
                 *
                 * 09:45 - 10:15
                 * 10:00 - 10:30
                 * 10:15 - 10:45
                 */
                boolean overlappingSlotExists =
                        pickupSlotRepository
                                .existsByBranchIdAndSlotDateAndStartTimeLessThanAndEndTimeGreaterThan(
                                        branchId,
                                        currentDate,
                                        currentEndTime,
                                        currentStartTime
                                );


                /*
                 * Existing overlapping slots are skipped.
                 *
                 * This makes the bulk operation safe to repeat
                 * without inserting duplicate windows.
                 */
                if (overlappingSlotExists) {

                    log.info(
                            "Skipping existing/overlapping pickup slot: " +
                                    "branchId={}, date={}, startTime={}, endTime={}",
                            branchId,
                            currentDate,
                            currentStartTime,
                            currentEndTime
                    );


                    currentStartTime =
                            currentEndTime;

                    continue;
                }


                /*
                 * =================================================
                 * CREATE ENTITY
                 * =================================================
                 */
                PickupSlot slot =
                        new PickupSlot();


                slot.setBranch(
                        branch
                );


                slot.setSlotDate(
                        currentDate
                );


                slot.setStartTime(
                        currentStartTime
                );


                slot.setEndTime(
                        currentEndTime
                );


                /*
                 * Normal capacity.
                 */
                slot.setCapacity(
                        request.capacity()
                );


                slot.setBookedCount(
                        0
                );


                /*
                 * New slot starts active.
                 */
                slot.setActive(
                        true
                );


                /*
                 * Priority configuration.
                 */
                slot.setPriorityEnabled(
                        priorityEnabled
                );


                slot.setPriorityCapacity(
                        priorityCapacity
                );


                slot.setPriorityBookedCount(
                        0
                );


                slot.setPriorityCharge(
                        priorityCharge
                );


                slotsToCreate.add(
                        slot
                );


                /*
                 * Move to next time window.
                 */
                currentStartTime =
                        currentEndTime;
            }


            /*
             * Move to next date.
             */
            currentDate =
                    currentDate.plusDays(
                            1
                    );
        }


        /*
         * =====================================================
         * SAVE ALL
         * =====================================================
         */
        if (
                slotsToCreate.isEmpty()
        ) {

            log.info(
                    "No new pickup slots required: branchId={}, startDate={}, endDate={}",
                    branchId,
                    request.startDate(),
                    request.endDate()
            );


            return List.of();
        }


        List<PickupSlot> savedSlots =
                pickupSlotRepository.saveAll(
                        slotsToCreate
                );


        log.info(
                "Pickup slots created successfully: " +
                        "branchId={}, startDate={}, endDate={}, createdCount={}",
                branchId,
                request.startDate(),
                request.endDate(),
                savedSlots.size()
        );


        return savedSlots
                .stream()
                .map(
                        PickupSlotResponse::from
                )
                .toList();
    }


    /*
     * =========================================================
     * ADMIN - UPDATE SINGLE PICKUP SLOT
     * =========================================================
     */
    @Transactional
    public PickupSlotResponse updateSlot(
            Long slotId,
            UpdatePickupSlotRequest request
    ) {

        log.info(
                "Updating pickup slot: slotId={}, date={}, " +
                        "startTime={}, endTime={}, capacity={}, active={}",
                slotId,
                request.slotDate(),
                request.startTime(),
                request.endTime(),
                request.capacity(),
                request.active()
        );


        PickupSlot slot =
                pickupSlotRepository
                        .findById(
                                slotId
                        )
                        .orElseThrow(
                                () -> {

                                    log.warn(
                                            "Pickup slot not found: slotId={}",
                                            slotId
                                    );


                                    return new IllegalArgumentException(
                                            "Pickup slot not found: "
                                                    + slotId
                                    );
                                }
                        );


        /*
         * Existing passed slot should not
         * be editable.
         */
        pickupSlotValidationService
                .validateNotPassed(
                        slot
                );


        /*
         * Validate new requested date/time.
         *
         * Notice:
         * We DO NOT hard-code a 30-minute duration.
         *
         * Slot duration is configurable during
         * bulk creation.
         */
        validateUpdateSchedule(
                request.slotDate(),
                request.startTime(),
                request.endTime()
        );


        validatePriorityInput(
                slot.getBranch().getId(),
                request.priorityEnabled(),
                request.priorityCapacity(),
                request.priorityCharge()
        );


        /*
         * Normal capacity cannot be reduced
         * below existing bookings.
         */
        if (
                request.capacity()
                        < slot.getBookedCount()
        ) {

            log.warn(
                    "Invalid pickup capacity update: " +
                            "slotId={}, requestedCapacity={}, bookedCount={}",
                    slotId,
                    request.capacity(),
                    slot.getBookedCount()
            );


            throw new IllegalArgumentException(
                    "Capacity cannot be less than " +
                            "the number of existing bookings."
            );
        }


        boolean priorityEnabled =
                Boolean.TRUE.equals(
                        request.priorityEnabled()
                );


        int priorityCapacity =
                request.priorityCapacity() != null
                        ? request.priorityCapacity()
                        : 0;


        BigDecimal priorityCharge =
                request.priorityCharge() != null
                        ? request.priorityCharge()
                        : BigDecimal.ZERO;


        /*
         * Priority capacity cannot be reduced
         * below already-booked priority positions.
         */
        if (
                priorityCapacity
                        < slot.getPriorityBookedCount()
        ) {

            log.warn(
                    "Cannot reduce priority capacity below existing bookings: " +
                            "slotId={}, requestedPriorityCapacity={}, priorityBookedCount={}",
                    slotId,
                    priorityCapacity,
                    slot.getPriorityBookedCount()
            );


            throw new IllegalArgumentException(
                    "Priority capacity cannot be less than " +
                            "existing priority bookings."
            );
        }


        /*
         * Once priority reservations exist,
         * priority cannot simply be disabled.
         */
        if (
                !priorityEnabled
                        &&
                        slot.getPriorityBookedCount() > 0
        ) {

            log.warn(
                    "Cannot disable priority pickup because bookings exist: " +
                            "slotId={}, priorityBookedCount={}",
                    slotId,
                    slot.getPriorityBookedCount()
            );


            throw new IllegalArgumentException(
                    "Priority pickup cannot be disabled " +
                            "because priority bookings already exist."
            );
        }


        if (!priorityEnabled) {

            priorityCapacity =
                    0;

            priorityCharge =
                    BigDecimal.ZERO;
        }


        /*
         * =====================================================
         * PREVENT OVERLAP WITH ANOTHER SLOT
         * =====================================================
         */
        boolean overlappingSlotExists =
                pickupSlotRepository
                        .existsByBranchIdAndSlotDateAndStartTimeLessThanAndEndTimeGreaterThanAndIdNot(
                                slot.getBranch().getId(),
                                request.slotDate(),
                                request.endTime(),
                                request.startTime(),
                                slotId
                        );


        if (overlappingSlotExists) {

            log.warn(
                    "Cannot update pickup slot because it overlaps: " +
                            "slotId={}, branchId={}, date={}, startTime={}, endTime={}",
                    slotId,
                    slot.getBranch().getId(),
                    request.slotDate(),
                    request.startTime(),
                    request.endTime()
            );


            throw new IllegalArgumentException(
                    "Pickup slot overlaps with an existing slot."
            );
        }


        /*
         * =====================================================
         * UPDATE FIELDS
         * =====================================================
         */
        slot.setSlotDate(
                request.slotDate()
        );


        slot.setStartTime(
                request.startTime()
        );


        slot.setEndTime(
                request.endTime()
        );


        slot.setCapacity(
                request.capacity()
        );


        slot.setActive(
                request.active()
        );


        slot.setPriorityEnabled(
                priorityEnabled
        );


        slot.setPriorityCapacity(
                priorityCapacity
        );


        slot.setPriorityCharge(
                priorityCharge
        );


        /*
         * IMPORTANT:
         *
         * Never reset:
         *
         * bookedCount
         * priorityBookedCount
         */


        PickupSlot savedSlot =
                pickupSlotRepository.save(
                        slot
                );


        log.info(
                "Pickup slot updated successfully: " +
                        "slotId={}, capacity={}, bookedCount={}, " +
                        "priorityEnabled={}, priorityCapacity={}, " +
                        "priorityBookedCount={}, priorityCharge={}, active={}",
                slotId,
                savedSlot.getCapacity(),
                savedSlot.getBookedCount(),
                savedSlot.isPriorityEnabled(),
                savedSlot.getPriorityCapacity(),
                savedSlot.getPriorityBookedCount(),
                savedSlot.getPriorityCharge(),
                savedSlot.isActive()
        );


        return PickupSlotResponse.from(
                savedSlot
        );
    }


    /*
     * =========================================================
     * ADMIN - DISABLE SINGLE PICKUP SLOT
     * =========================================================
     */
    @Transactional
    public void disableSlot(
            Long slotId
    ) {

        log.info(
                "Disabling pickup slot: slotId={}",
                slotId
        );


        PickupSlot slot =
                pickupSlotRepository
                        .findById(
                                slotId
                        )
                        .orElseThrow(
                                () -> {

                                    log.warn(
                                            "Cannot disable pickup slot. " +
                                                    "Slot not found: slotId={}",
                                            slotId
                                    );


                                    return new IllegalArgumentException(
                                            "Pickup slot not found: "
                                                    + slotId
                                    );
                                }
                        );


        pickupSlotValidationService
                .validateNotPassed(
                        slot
                );


        slot.setActive(
                false
        );


        pickupSlotRepository.save(
                slot
        );


        log.info(
                "Pickup slot disabled successfully: slotId={}",
                slotId
        );
    }


    /*
     * =========================================================
     * VALIDATE BULK CREATE REQUEST
     * =========================================================
     */
    private void validateCreateRequest(
            CreatePickupSlotRequest request
    ) {

        /*
         * Required values should already be handled
         * by Jakarta validation, but keeping defensive
         * service validation is useful.
         */
        if (
                request.startDate() == null
                        ||
                        request.endDate() == null
        ) {

            throw new IllegalArgumentException(
                    "Start date and end date are required."
            );
        }


        if (
                request.startTime() == null
                        ||
                        request.endTime() == null
        ) {

            throw new IllegalArgumentException(
                    "Start time and end time are required."
            );
        }


        if (
                request.slotDurationMinutes() == null
                        ||
                        request.slotDurationMinutes() <= 0
        ) {

            throw new IllegalArgumentException(
                    "Slot duration must be greater than zero."
            );
        }


        /*
         * Date range must be forward.
         */
        if (
                request.startDate()
                        .isAfter(
                                request.endDate()
                        )
        ) {

            throw new IllegalArgumentException(
                    "Start date cannot be after end date."
            );
        }


        LocalDate today =
                LocalDate.now(
                        BUSINESS_ZONE
                );


        /*
         * Do not create pickup schedules entirely
         * beginning in the past.
         */
        if (
                request.startDate()
                        .isBefore(today)
        ) {

            throw new IllegalArgumentException(
                    "Start date cannot be in the past."
            );
        }


        /*
         * Daily time range must be valid.
         */
        validateSlotTime(
                request.startTime(),
                request.endTime()
        );


        long totalWindowMinutes =
                Duration.between(
                        request.startTime(),
                        request.endTime()
                ).toMinutes();


        /*
         * Duration cannot be greater than
         * the daily operating window.
         */
        if (
                request.slotDurationMinutes()
                        > totalWindowMinutes
        ) {

            throw new IllegalArgumentException(
                    "Slot duration cannot be greater " +
                            "than the pickup time range."
            );
        }


        /*
         * Do not create a partial final slot.
         *
         * Example:
         *
         * 09:00 -> 10:00 = 60 minutes
         * duration = 30
         *
         * valid.
         *
         * 09:00 -> 10:10 = 70 minutes
         * duration = 30
         *
         * invalid.
         */
        if (
                totalWindowMinutes
                        % request.slotDurationMinutes()
                        != 0
        ) {

            throw new IllegalArgumentException(
                    "Pickup time range must be exactly divisible " +
                            "by slot duration."
            );
        }
    }


    /*
     * =========================================================
     * VALIDATE SINGLE SLOT UPDATE
     * =========================================================
     */
    private void validateUpdateSchedule(
            LocalDate slotDate,
            LocalTime startTime,
            LocalTime endTime
    ) {

        if (
                slotDate == null
        ) {

            throw new IllegalArgumentException(
                    "Pickup slot date is required."
            );
        }


        validateSlotTime(
                startTime,
                endTime
        );


        LocalDate today =
                LocalDate.now(
                        BUSINESS_ZONE
                );


        if (
                slotDate.isBefore(
                        today
                )
        ) {

            throw new IllegalArgumentException(
                    "Pickup slot date cannot be in the past."
            );
        }


        /*
         * If updating today's slot,
         * new start time must still be future.
         */
        if (
                slotDate.isEqual(
                        today
                )
        ) {

            LocalTime now =
                    LocalTime.now(
                            BUSINESS_ZONE
                    );


            if (
                    !startTime.isAfter(
                            now
                    )
            ) {

                throw new IllegalArgumentException(
                        "Pickup slot start time must be in the future."
                );
            }
        }
    }


    /*
     * =========================================================
     * BASIC TIME VALIDATION
     * =========================================================
     */
    private void validateSlotTime(
            LocalTime startTime,
            LocalTime endTime
    ) {

        if (
                startTime == null
                        ||
                        endTime == null
        ) {

            throw new IllegalArgumentException(
                    "Pickup slot start time and end time are required."
            );
        }


        if (
                !endTime.isAfter(
                        startTime
                )
        ) {

            throw new IllegalArgumentException(
                    "Pickup slot end time must be after start time."
            );
        }
    }


    /*
     * =========================================================
     * PRIORITY VALIDATION
     * =========================================================
     */
    private void validatePriorityInput(
            Long branchId,
            Boolean priorityEnabledValue,
            Integer priorityCapacity,
            BigDecimal priorityCharge
    ) {

        boolean priorityEnabled =
                Boolean.TRUE.equals(
                        priorityEnabledValue
                );


        if (
                priorityCapacity != null
                        &&
                        priorityCapacity < 0
        ) {

            log.warn(
                    "Invalid priority capacity: " +
                            "branchId={}, priorityCapacity={}",
                    branchId,
                    priorityCapacity
            );


            throw new IllegalArgumentException(
                    "Priority capacity cannot be negative."
            );
        }


        if (
                priorityCharge != null
                        &&
                        priorityCharge.compareTo(
                                BigDecimal.ZERO
                        ) < 0
        ) {

            log.warn(
                    "Invalid priority charge: " +
                            "branchId={}, priorityCharge={}",
                    branchId,
                    priorityCharge
            );


            throw new IllegalArgumentException(
                    "Priority charge cannot be negative."
            );
        }


        int resolvedPriorityCapacity =
                priorityCapacity != null
                        ? priorityCapacity
                        : 0;


        if (
                priorityEnabled
                        &&
                        resolvedPriorityCapacity <= 0
        ) {

            log.warn(
                    "Priority pickup enabled with invalid capacity: " +
                            "branchId={}, priorityCapacity={}",
                    branchId,
                    resolvedPriorityCapacity
            );


            throw new IllegalArgumentException(
                    "Priority capacity must be greater than zero " +
                            "when priority pickup is enabled."
            );
        }
    }
}