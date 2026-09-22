package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.staff.StaffUserRepository;
import com.gokulsweets.restaurant.staff.payroll.dto.CompensationResponse;
import com.gokulsweets.restaurant.staff.payroll.dto.PayrollSummaryResponse;
import com.gokulsweets.restaurant.staff.payroll.dto.SetCompensationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPayrollService {

    private final StaffUserRepository
            staffUserRepository;

    private final StaffCompensationProfileRepository
            compensationRepository;

    private final StaffEarningRepository
            earningRepository;

    private final StaffPaymentRequestRepository
            paymentRequestRepository;

    private final PayrollEarningSyncService
            earningSyncService;

    private final PayrollBalanceService
            payrollBalanceService;

    private final PayrollStaffLockRepository
            staffLockRepository;

    private final PayrollAuthorizationService
            payrollAuthorizationService;


    @Transactional
    public CompensationResponse setCompensation(
            Long staffUserId,
            SetCompensationRequest request
    ) {

        StaffUser actor =
                payrollAuthorizationService
                        .requireManage();

        StaffUser target =
                getTarget(
                        staffUserId
                );

        payrollAuthorizationService
                .requireTargetScope(
                        actor,
                        target
                );

        if (
                request.dailyRate()
                        .signum() < 0
                        ||
                        request.halfDayRate()
                                .signum() < 0
        ) {

            throw new IllegalArgumentException(
                    "Compensation rates cannot be negative."
            );
        }

        StaffCompensationProfile profile =
                new StaffCompensationProfile();

        profile.setStaffUser(
                target
        );

        profile.setEffectiveFrom(
                request.effectiveFrom()
        );

        profile.setDailyRate(
                money(
                        request.dailyRate()
                )
        );

        profile.setHalfDayRate(
                money(
                        request.halfDayRate()
                )
        );

        profile.setCreatedByStaffUser(
                actor
        );

        StaffCompensationProfile saved =
                compensationRepository
                        .save(
                                profile
                        );

        log.info(
                "Staff compensation created: compensationId={}, staffUserId={}, effectiveFrom={}, createdByStaffUserId={}",
                saved.getId(),
                target.getId(),
                saved.getEffectiveFrom(),
                actor.getId()
        );

        return toCompensationResponse(
                saved
        );
    }


    @Transactional(readOnly = true)
    public List<CompensationResponse> getCompensationHistory(
            Long staffUserId
    ) {

        StaffUser actor =
                payrollAuthorizationService
                        .requireView();

        StaffUser target =
                getTarget(
                        staffUserId
                );

        payrollAuthorizationService
                .requireTargetScope(
                        actor,
                        target
                );

        return compensationRepository
                .findByStaffUserIdOrderByEffectiveFromDesc(
                        staffUserId
                )
                .stream()
                .map(
                        this::toCompensationResponse
                )
                .toList();
    }


    @Transactional
    public PayrollSummaryResponse getSummary(
            Long staffUserId
    ) {

        StaffUser actor =
                payrollAuthorizationService
                        .requireView();

        StaffUser target =
                getTarget(
                        staffUserId
                );

        payrollAuthorizationService
                .requireTargetScope(
                        actor,
                        target
                );

        StaffUser locked =
                staffLockRepository
                        .findByIdForUpdate(
                                target.getId()
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Staff user does not exist."
                                        )
                        );

        earningSyncService
                .syncApprovedAttendance(
                        locked.getId()
                );

        return payrollBalanceService
                .buildSummary(
                        locked
                );
    }


    private StaffUser getTarget(
            Long staffUserId
    ) {

        return staffUserRepository
                .findDetailedById(
                        staffUserId
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Staff user does not exist."
                                )
                );
    }


    private CompensationResponse toCompensationResponse(
            StaffCompensationProfile profile
    ) {

        return new CompensationResponse(
                profile.getId(),
                profile.getStaffUser()
                        .getId(),
                profile.getEffectiveFrom(),
                profile.getDailyRate(),
                profile.getHalfDayRate(),
                profile.getCreatedByStaffUser()
                        .getFullName(),
                profile.getCreatedAt()
        );
    }


    private BigDecimal money(
            BigDecimal value
    ) {

        return value.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }
}
