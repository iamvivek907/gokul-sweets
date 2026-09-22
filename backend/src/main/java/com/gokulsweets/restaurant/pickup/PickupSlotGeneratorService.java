package com.gokulsweets.restaurant.pickup;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.pickup.dto.PickupSlotResponse;
import com.gokulsweets.restaurant.pickup.repository.PickupSlotRepository;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PickupSlotGeneratorService {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(
                    "Asia/Kolkata"
            );


    private final BranchRepository
            branchRepository;

    private final BranchPickupSettingsRepository
            branchPickupSettingsRepository;

    private final PickupSlotRepository
            pickupSlotRepository;

    private final StaffAuthorizationService
            staffAuthorizationService;


    @Transactional
    public List<PickupSlotResponse> generateSlots(
            Long branchId,
            LocalDate slotDate
    ) {

        staffAuthorizationService
                .requirePermission(
                        PermissionName.BRANCH_MANAGE
                );


        staffAuthorizationService
                .requireBranchAccess(
                        branchId
                );


        log.info(
                "Generating pickup slots: branchId={}, date={}",
                branchId,
                slotDate
        );


        Branch branch =
                branchRepository
                        .findById(
                                branchId
                        )
                        .orElseThrow(
                                () -> {

                                    log.warn(
                                            "Cannot generate pickup slots: branch not found. branchId={}",
                                            branchId
                                    );


                                    return new IllegalArgumentException(
                                            "Branch not found."
                                    );
                                }
                        );


        BranchPickupSettings settings =
                branchPickupSettingsRepository
                        .findByBranchId(
                                branchId
                        )
                        .orElseThrow(
                                () -> {

                                    log.warn(
                                            "Cannot generate pickup slots: settings not found. branchId={}",
                                            branchId
                                    );


                                    return new IllegalArgumentException(
                                            "Pickup settings not found for this branch."
                                    );
                                }
                        );


        if (
                !settings.isEnabled()
        ) {

            log.warn(
                    "Pickup slot generation skipped because pickup is disabled. branchId={}",
                    branchId
            );


            return List.of();
        }


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
                    "Cannot generate pickup slots for a past date."
            );
        }


        LocalDate maxBookingDate =
                today.plusDays(
                        settings.getAdvanceBookingDays()
                );


        if (
                slotDate.isAfter(
                        maxBookingDate
                )
        ) {

            throw new IllegalArgumentException(
                    "Pickup slot date is beyond the advance booking window."
            );
        }


        LocalTime openingTime =
                settings.getOpeningTime();


        LocalTime closingTime =
                settings.getClosingTime();


        int durationMinutes =
                settings.getSlotDurationMinutes();


        int defaultCapacity =
                settings.getDefaultCapacity();


        boolean priorityEnabled =
                settings.isPriorityEnabled();


        int priorityCapacity =
                priorityEnabled
                        ? settings.getDefaultPriorityCapacity()
                        : 0;


        BigDecimal priorityCharge =
                priorityEnabled
                        ? settings.getDefaultPriorityCharge()
                        : BigDecimal.ZERO;


        List<PickupSlot> generatedSlots =
                new ArrayList<>();


        LocalTime currentTime =
                openingTime;


        while (
                currentTime
                        .plusMinutes(
                                durationMinutes
                        )
                        .compareTo(
                                closingTime
                        )
                        <= 0
        ) {

            LocalTime endTime =
                    currentTime
                            .plusMinutes(
                                    durationMinutes
                            );


            Optional<PickupSlot> existingSlot =
                    pickupSlotRepository
                            .findByBranchIdAndSlotDateAndStartTimeAndEndTime(
                                    branchId,
                                    slotDate,
                                    currentTime,
                                    endTime
                            );


            if (
                    existingSlot.isPresent()
            ) {

                log.debug(
                        "Pickup slot already exists, skipping: branchId={}, date={}, startTime={}, endTime={}",
                        branchId,
                        slotDate,
                        currentTime,
                        endTime
                );

            } else {

                PickupSlot slot =
                        new PickupSlot();


                slot.setBranch(
                        branch
                );


                slot.setSlotDate(
                        slotDate
                );


                slot.setStartTime(
                        currentTime
                );


                slot.setEndTime(
                        endTime
                );


                slot.setCapacity(
                        defaultCapacity
                );


                slot.setBookedCount(
                        0
                );


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


                slot.setActive(
                        true
                );


                PickupSlot savedSlot =
                        pickupSlotRepository
                                .save(
                                        slot
                                );


                generatedSlots.add(
                        savedSlot
                );


                log.info(
                        "Pickup slot created: slotId={}, branchId={}, date={}, startTime={}, endTime={}, capacity={}, priorityEnabled={}, priorityCapacity={}",
                        savedSlot.getId(),
                        branchId,
                        slotDate,
                        currentTime,
                        endTime,
                        defaultCapacity,
                        priorityEnabled,
                        priorityCapacity
                );
            }


            currentTime =
                    endTime;
        }


        log.info(
                "Pickup slot generation completed: branchId={}, date={}, createdCount={}",
                branchId,
                slotDate,
                generatedSlots.size()
        );


        return generatedSlots
                .stream()
                .map(
                        PickupSlotResponse::from
                )
                .toList();
    }
}