package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.staff.StaffUserRepository;
import com.gokulsweets.restaurant.staff.payroll.dto.PayrollOpeningBalanceResponse;
import com.gokulsweets.restaurant.staff.payroll.dto.SetPayrollOpeningBalanceRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Coordinates admin payroll opening balance operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPayrollOpeningBalanceService {

    private final StaffUserRepository staffUserRepository;

    private final StaffPayrollOpeningBalanceRepository openingBalanceRepository;

    private final PayrollAuthorizationService payrollAuthorizationService;

    private final PayrollStaffLockRepository staffLockRepository;

    /**
     * Returns opening balance.
     *
     * @param staffUserId the staff user id
     * @return the get opening balance result
     */
    @Transactional(readOnly = true)
    public PayrollOpeningBalanceResponse getOpeningBalance(Long staffUserId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminPayrollOpeningBalanceService.class, "getOpeningBalance(Long)");
        try {
            StaffUser actor = payrollAuthorizationService.requireView();
            StaffUser target = getTarget(staffUserId);
            payrollAuthorizationService.requireTargetScope(actor, target);
            return openingBalanceRepository
                    .findByStaffUserId(staffUserId)
                    .map(this::toResponse)
                    .orElse(null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPayrollOpeningBalanceService.class,
                    "getOpeningBalance(Long)");
        }
    }

    /**
     * Creates opening balance.
     *
     * @param staffUserId the staff user id
     * @param request the request
     * @return the create opening balance result
     */
    @Transactional
    public PayrollOpeningBalanceResponse createOpeningBalance(
            Long staffUserId, SetPayrollOpeningBalanceRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminPayrollOpeningBalanceService.class,
                        "createOpeningBalance(Long,SetPayrollOpeningBalanceRequest)");
        try {
            StaffUser actor = payrollAuthorizationService.requireManage();
            StaffUser target = getTarget(staffUserId);
            payrollAuthorizationService.requireTargetScope(actor, target);
            staffLockRepository
                    .findByIdForUpdate(target.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Staff user does not exist."));
            if (openingBalanceRepository.existsByStaffUserId(target.getId())) {
                throw new IllegalStateException(
                        "Opening payroll balance has already been configured for this staff"
                                + " member.");
            }
            BigDecimal earned = money(request.earnedAmount());
            BigDecimal taken = money(request.takenAmount());
            if (taken.compareTo(earned) > 0) {
                throw new IllegalArgumentException(
                        "Previously taken amount cannot be greater than previously earned amount.");
            }
            StaffPayrollOpeningBalance opening = new StaffPayrollOpeningBalance();
            opening.setStaffUser(target);
            opening.setAsOfDate(request.asOfDate());
            opening.setEarnedAmount(earned);
            opening.setTakenAmount(taken);
            opening.setNote(normalizeNullable(request.note()));
            opening.setCreatedByStaffUser(actor);
            StaffPayrollOpeningBalance saved = openingBalanceRepository.save(opening);
            log.info(
                    "Payroll opening balance created: openingBalanceId={}, staffUserId={},"
                        + " asOfDate={}, earnedAmount={}, takenAmount={}, createdByStaffUserId={}",
                    saved.getId(),
                    target.getId(),
                    saved.getAsOfDate(),
                    saved.getEarnedAmount(),
                    saved.getTakenAmount(),
                    actor.getId());
            return toResponse(saved);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPayrollOpeningBalanceService.class,
                    "createOpeningBalance(Long,SetPayrollOpeningBalanceRequest)");
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
                MethodTiming.start(AdminPayrollOpeningBalanceService.class, "getTarget(Long)");
        try {
            return staffUserRepository
                    .findDetailedById(staffUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Staff user does not exist."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPayrollOpeningBalanceService.class,
                    "getTarget(Long)");
        }
    }

    /**
     * Tos response.
     *
     * @param opening the opening
     * @return the to response result
     */
    private PayrollOpeningBalanceResponse toResponse(StaffPayrollOpeningBalance opening) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminPayrollOpeningBalanceService.class,
                        "toResponse(StaffPayrollOpeningBalance)");
        try {
            BigDecimal net = opening.getEarnedAmount().subtract(opening.getTakenAmount());
            return new PayrollOpeningBalanceResponse(
                    opening.getId(),
                    opening.getStaffUser().getId(),
                    opening.getAsOfDate(),
                    opening.getEarnedAmount(),
                    opening.getTakenAmount(),
                    money(net),
                    opening.getNote(),
                    opening.getCreatedByStaffUser().getFullName(),
                    opening.getCreatedAt());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPayrollOpeningBalanceService.class,
                    "toResponse(StaffPayrollOpeningBalance)");
        }
    }

    /**
     * Rounds the monetary amount to two decimal places using half-up rounding. A null amount is
     * treated as zero.
     *
     * @param value the value supplied to this method
     * @return the value of {@code (value == null ? BigDecimal.ZERO : value).setScale(2,
     *     RoundingMode.HALF_UP)}
     */
    private BigDecimal money(BigDecimal value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminPayrollOpeningBalanceService.class, "money(BigDecimal)");
        try {
            return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPayrollOpeningBalanceService.class,
                    "money(BigDecimal)");
        }
    }

    /**
     * Normalizes nullable.
     *
     * @param value the value
     * @return the normalize nullable result
     */
    private String normalizeNullable(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminPayrollOpeningBalanceService.class, "normalizeNullable(String)");
        try {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPayrollOpeningBalanceService.class,
                    "normalizeNullable(String)");
        }
    }
}
