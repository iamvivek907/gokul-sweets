package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;
import com.gokulsweets.restaurant.staff.payroll.dto.PayrollSummaryResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;

/** Coordinates payroll balance operations. */
@Service
@RequiredArgsConstructor
public class PayrollBalanceService {

    private static final EnumSet<ApprovalRequestStatus> COMMITTED_STATUSES =
            EnumSet.of(
                    ApprovalRequestStatus.PENDING,
                    ApprovalRequestStatus.APPROVED,
                    ApprovalRequestStatus.SENT_BACK);

    private final StaffEarningRepository earningRepository;

    private final StaffPaymentRequestRepository paymentRequestRepository;

    private final StaffPayrollOpeningBalanceRepository openingBalanceRepository;

    /**
     * Builds summary.
     *
     * @param staff the staff
     * @return the build summary result
     */
    @Transactional(readOnly = true)
    public PayrollSummaryResponse buildSummary(StaffUser staff) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PayrollBalanceService.class, "buildSummary(StaffUser)");
        try {
            BigDecimal ledgerEarned =
                    money(earningRepository.sumAmountByStaffUserId(staff.getId()));
            BigDecimal committedRequests =
                    money(
                            paymentRequestRepository.sumCommittedAmount(
                                    staff.getId(), COMMITTED_STATUSES, null));
            StaffPayrollOpeningBalance opening =
                    openingBalanceRepository.findByStaffUserId(staff.getId()).orElse(null);
            BigDecimal openingEarned =
                    opening == null ? BigDecimal.ZERO : money(opening.getEarnedAmount());
            BigDecimal openingTaken =
                    opening == null ? BigDecimal.ZERO : money(opening.getTakenAmount());
            BigDecimal totalEarned = money(openingEarned.add(ledgerEarned));
            BigDecimal committed = money(openingTaken.add(committedRequests));
            BigDecimal available = totalEarned.subtract(committed);
            if (available.signum() < 0) {
                available = BigDecimal.ZERO;
            }
            return new PayrollSummaryResponse(
                    staff.getId(), staff.getFullName(), totalEarned, committed, money(available));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PayrollBalanceService.class,
                    "buildSummary(StaffUser)");
        }
    }

    /**
     * Calculates available.
     *
     * @param staffUserId the staff user id
     * @param excludePaymentRequestId the exclude payment request id
     * @return the calculate available result
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateAvailable(Long staffUserId, Long excludePaymentRequestId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PayrollBalanceService.class, "calculateAvailable(Long,Long)");
        try {
            BigDecimal ledgerEarned = money(earningRepository.sumAmountByStaffUserId(staffUserId));
            BigDecimal committedRequests =
                    money(
                            paymentRequestRepository.sumCommittedAmount(
                                    staffUserId, COMMITTED_STATUSES, excludePaymentRequestId));
            StaffPayrollOpeningBalance opening =
                    openingBalanceRepository.findByStaffUserId(staffUserId).orElse(null);
            BigDecimal openingEarned =
                    opening == null ? BigDecimal.ZERO : money(opening.getEarnedAmount());
            BigDecimal openingTaken =
                    opening == null ? BigDecimal.ZERO : money(opening.getTakenAmount());
            BigDecimal available =
                    openingEarned
                            .add(ledgerEarned)
                            .subtract(openingTaken)
                            .subtract(committedRequests);
            return available.signum() < 0 ? BigDecimal.ZERO : money(available);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PayrollBalanceService.class,
                    "calculateAvailable(Long,Long)");
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
                MethodTiming.start(PayrollBalanceService.class, "money(BigDecimal)");
        try {
            return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PayrollBalanceService.class, "money(BigDecimal)");
        }
    }
}
