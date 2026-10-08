package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.observability.MethodTiming;
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

/** Coordinates admin payroll operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPayrollService {

    private final StaffUserRepository staffUserRepository;

    private final StaffCompensationProfileRepository compensationRepository;

    private final StaffEarningRepository earningRepository;

    private final StaffPaymentRequestRepository paymentRequestRepository;

    private final PayrollEarningSyncService earningSyncService;

    private final PayrollBalanceService payrollBalanceService;

    private final PayrollStaffLockRepository staffLockRepository;

    private final PayrollAuthorizationService payrollAuthorizationService;

    /**
     * Updates compensation.
     *
     * @param staffUserId the staff user id
     * @param request the request
     * @return the set compensation result
     */
    @Transactional
    public CompensationResponse setCompensation(Long staffUserId, SetCompensationRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminPayrollService.class, "setCompensation(Long,SetCompensationRequest)");
        try {
            StaffUser actor = payrollAuthorizationService.requireManage();
            StaffUser target = getTarget(staffUserId);
            payrollAuthorizationService.requireTargetScope(actor, target);
            if (request.dailyRate().signum() < 0 || request.halfDayRate().signum() < 0) {
                throw new IllegalArgumentException("Compensation rates cannot be negative.");
            }
            StaffCompensationProfile profile = new StaffCompensationProfile();
            profile.setStaffUser(target);
            profile.setEffectiveFrom(request.effectiveFrom());
            profile.setDailyRate(money(request.dailyRate()));
            profile.setHalfDayRate(money(request.halfDayRate()));
            profile.setCreatedByStaffUser(actor);
            StaffCompensationProfile saved = compensationRepository.save(profile);
            log.info(
                    "Staff compensation created: compensationId={}, staffUserId={},"
                            + " effectiveFrom={}, createdByStaffUserId={}",
                    saved.getId(),
                    target.getId(),
                    saved.getEffectiveFrom(),
                    actor.getId());
            return toCompensationResponse(saved);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPayrollService.class,
                    "setCompensation(Long,SetCompensationRequest)");
        }
    }

    /**
     * Returns compensation history.
     *
     * @param staffUserId the staff user id
     * @return the get compensation history result
     */
    @Transactional(readOnly = true)
    public List<CompensationResponse> getCompensationHistory(Long staffUserId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminPayrollService.class, "getCompensationHistory(Long)");
        try {
            StaffUser actor = payrollAuthorizationService.requireView();
            StaffUser target = getTarget(staffUserId);
            payrollAuthorizationService.requireTargetScope(actor, target);
            return compensationRepository
                    .findByStaffUserIdOrderByEffectiveFromDesc(staffUserId)
                    .stream()
                    .map(this::toCompensationResponse)
                    .toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPayrollService.class,
                    "getCompensationHistory(Long)");
        }
    }

    /**
     * Returns summary.
     *
     * @param staffUserId the staff user id
     * @return the get summary result
     */
    @Transactional
    public PayrollSummaryResponse getSummary(Long staffUserId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminPayrollService.class, "getSummary(Long)");
        try {
            StaffUser actor = payrollAuthorizationService.requireView();
            StaffUser target = getTarget(staffUserId);
            payrollAuthorizationService.requireTargetScope(actor, target);
            StaffUser locked =
                    staffLockRepository
                            .findByIdForUpdate(target.getId())
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Staff user does not exist."));
            earningSyncService.syncApprovedAttendance(locked.getId());
            return payrollBalanceService.buildSummary(locked);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminPayrollService.class, "getSummary(Long)");
        }
    }

    /**
     * Returns target.
     *
     * @param staffUserId the staff user id
     * @return the get target result
     */
    private StaffUser getTarget(Long staffUserId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminPayrollService.class, "getTarget(Long)");
        try {
            return staffUserRepository
                    .findDetailedById(staffUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Staff user does not exist."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminPayrollService.class, "getTarget(Long)");
        }
    }

    /**
     * Tos compensation response.
     *
     * @param profile the profile
     * @return the to compensation response result
     */
    private CompensationResponse toCompensationResponse(StaffCompensationProfile profile) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminPayrollService.class,
                        "toCompensationResponse(StaffCompensationProfile)");
        try {
            return new CompensationResponse(
                    profile.getId(),
                    profile.getStaffUser().getId(),
                    profile.getEffectiveFrom(),
                    profile.getDailyRate(),
                    profile.getHalfDayRate(),
                    profile.getCreatedByStaffUser().getFullName(),
                    profile.getCreatedAt());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPayrollService.class,
                    "toCompensationResponse(StaffCompensationProfile)");
        }
    }

    /**
     * Money the operation.
     *
     * @param value the value
     * @return the money result
     */
    private BigDecimal money(BigDecimal value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminPayrollService.class, "money(BigDecimal)");
        try {
            return value.setScale(2, RoundingMode.HALF_UP);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminPayrollService.class, "money(BigDecimal)");
        }
    }
}
