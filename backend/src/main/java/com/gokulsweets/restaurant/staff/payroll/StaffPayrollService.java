package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequest;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequestType;
import com.gokulsweets.restaurant.staff.approval.ApprovalWorkflowService;
import com.gokulsweets.restaurant.staff.payroll.dto.CreatePaymentRequest;
import com.gokulsweets.restaurant.staff.payroll.dto.EarningResponse;
import com.gokulsweets.restaurant.staff.payroll.dto.PaymentRequestResponse;
import com.gokulsweets.restaurant.staff.payroll.dto.PayrollActionRequest;
import com.gokulsweets.restaurant.staff.payroll.dto.PayrollBranchOptionResponse;
import com.gokulsweets.restaurant.staff.payroll.dto.PayrollOptionsResponse;
import com.gokulsweets.restaurant.staff.payroll.dto.PayrollSummaryResponse;
import com.gokulsweets.restaurant.staff.payroll.dto.UpdatePaymentRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.List;

/** Coordinates staff payroll operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaffPayrollService {

    private static final EnumSet<ApprovalRequestStatus> COMMITTED_STATUSES =
            EnumSet.of(
                    ApprovalRequestStatus.PENDING,
                    ApprovalRequestStatus.APPROVED,
                    ApprovalRequestStatus.SENT_BACK);

    private final StaffAuthorizationService staffAuthorizationService;

    private final BranchRepository branchRepository;

    private final PayrollStaffLockRepository staffLockRepository;

    private final StaffEarningRepository earningRepository;

    private final StaffPaymentRequestRepository paymentRequestRepository;

    private final PayrollEarningSyncService earningSyncService;

    private final PayrollBalanceService payrollBalanceService;

    private final ApprovalWorkflowService approvalWorkflowService;

    /**
     * Returns options.
     *
     * @return the get options result
     */
    @Transactional(readOnly = true)
    public PayrollOptionsResponse getOptions() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffPayrollService.class, "getOptions()");
        try {
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            List<PayrollBranchOptionResponse> branches =
                    staff.getBranches().stream()
                            .sorted(
                                    java.util.Comparator.comparing(
                                                    Branch::getName, String.CASE_INSENSITIVE_ORDER)
                                            .thenComparing(Branch::getId))
                            .map(
                                    branch ->
                                            new PayrollBranchOptionResponse(
                                                    branch.getId(),
                                                    branch.getCode(),
                                                    branch.getName(),
                                                    branch.isActive()))
                            .toList();
            return new PayrollOptionsResponse(branches);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffPayrollService.class, "getOptions()");
        }
    }

    /**
     * Returns my summary.
     *
     * @return the get my summary result
     */
    @Transactional
    public PayrollSummaryResponse getMySummary() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffPayrollService.class, "getMySummary()");
        try {
            StaffUser current = staffAuthorizationService.getCurrentStaff();
            StaffUser locked =
                    staffLockRepository
                            .findByIdForUpdate(current.getId())
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Staff user does not exist."));
            earningSyncService.syncApprovedAttendance(locked.getId());
            return payrollBalanceService.buildSummary(locked);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffPayrollService.class, "getMySummary()");
        }
    }

    /**
     * Returns my earnings.
     *
     * @param page the page
     * @param size the size
     * @return the get my earnings result
     */
    @Transactional
    public Page<EarningResponse> getMyEarnings(int page, int size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffPayrollService.class, "getMyEarnings(int,int)");
        try {
            StaffUser current = staffAuthorizationService.getCurrentStaff();
            StaffUser staff =
                    staffLockRepository
                            .findByIdForUpdate(current.getId())
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Staff user does not exist."));
            earningSyncService.syncApprovedAttendance(staff.getId());
            return earningRepository
                    .findByStaffUserId(
                            staff.getId(),
                            PageRequest.of(
                                    Math.max(page, 0),
                                    Math.min(Math.max(size, 1), 100),
                                    Sort.by(Sort.Direction.DESC, "earningDate")
                                            .and(Sort.by(Sort.Direction.DESC, "id"))))
                    .map(this::toEarningResponse);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffPayrollService.class, "getMyEarnings(int,int)");
        }
    }

    /**
     * Returns my payment requests.
     *
     * @param page the page
     * @param size the size
     * @return the get my payment requests result
     */
    @Transactional(readOnly = true)
    public Page<PaymentRequestResponse> getMyPaymentRequests(int page, int size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffPayrollService.class, "getMyPaymentRequests(int,int)");
        try {
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            return paymentRequestRepository
                    .findByStaffUserId(
                            staff.getId(),
                            PageRequest.of(
                                    Math.max(page, 0),
                                    Math.min(Math.max(size, 1), 100),
                                    Sort.by(Sort.Direction.DESC, "createdAt")
                                            .and(Sort.by(Sort.Direction.DESC, "id"))))
                    .map(this::toPaymentResponse);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPayrollService.class,
                    "getMyPaymentRequests(int,int)");
        }
    }

    /**
     * Creates payment request.
     *
     * @param request the request
     * @return the create payment request result
     */
    @Transactional
    public PaymentRequestResponse createPaymentRequest(CreatePaymentRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffPayrollService.class, "createPaymentRequest(CreatePaymentRequest)");
        try {
            StaffUser current = staffAuthorizationService.getCurrentStaff();
            StaffUser staff =
                    staffLockRepository
                            .findByIdForUpdate(current.getId())
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Staff user does not exist."));
            Branch branch = getAccessibleBranch(current, request.branchId());
            earningSyncService.syncApprovedAttendance(staff.getId());
            BigDecimal amount = normalizePositiveAmount(request.amount());
            BigDecimal available = payrollBalanceService.calculateAvailable(staff.getId(), null);
            if (amount.compareTo(available) > 0) {
                throw new IllegalStateException(
                        "Requested amount exceeds currently available earned balance.");
            }
            String note = normalizeNullable(request.note());
            ApprovalRequest approval =
                    approvalWorkflowService.createRequest(
                            ApprovalRequestType.PAYMENT,
                            staff,
                            branch,
                            "Money taken request - ₹" + amount.toPlainString(),
                            createPaymentSummary(amount, note));
            StaffPaymentRequest payment = new StaffPaymentRequest();
            payment.setStaffUser(staff);
            payment.setBranch(branch);
            payment.setApprovalRequest(approval);
            payment.setAmount(amount);
            payment.setNote(note);
            StaffPaymentRequest saved = paymentRequestRepository.save(payment);
            log.info(
                    "Staff payment request created: paymentRequestId={}, approvalRequestId={},"
                            + " staffUserId={}, amount={}",
                    saved.getId(),
                    approval.getId(),
                    staff.getId(),
                    saved.getAmount());
            return toPaymentResponse(saved);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPayrollService.class,
                    "createPaymentRequest(CreatePaymentRequest)");
        }
    }

    /**
     * Updates sent back payment.
     *
     * @param paymentRequestId the payment request id
     * @param request the request
     * @return the update sent back payment result
     */
    @Transactional
    public PaymentRequestResponse updateSentBackPayment(
            Long paymentRequestId, UpdatePaymentRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffPayrollService.class,
                        "updateSentBackPayment(Long,UpdatePaymentRequest)");
        try {
            StaffUser current = staffAuthorizationService.getCurrentStaff();
            StaffUser staff =
                    staffLockRepository
                            .findByIdForUpdate(current.getId())
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Staff user does not exist."));
            StaffPaymentRequest payment = getOwnPayment(paymentRequestId, staff.getId());
            if (payment.getApprovalRequest().getStatus() != ApprovalRequestStatus.SENT_BACK) {
                throw new IllegalStateException(
                        "Only a sent-back payment request can be corrected.");
            }
            Branch branch = getAccessibleBranch(current, request.branchId());
            earningSyncService.syncApprovedAttendance(staff.getId());
            BigDecimal amount = normalizePositiveAmount(request.amount());
            BigDecimal availableExcludingCurrent =
                    payrollBalanceService.calculateAvailable(staff.getId(), paymentRequestId);
            if (amount.compareTo(availableExcludingCurrent) > 0) {
                throw new IllegalStateException(
                        "Corrected amount exceeds currently available earned balance.");
            }
            String note = normalizeNullable(request.note());
            payment.setBranch(branch);
            payment.setAmount(amount);
            payment.setNote(note);
            ApprovalRequest approval = payment.getApprovalRequest();
            approval.setBranch(branch);
            approval.setTitle("Money taken request - ₹" + amount.toPlainString());
            approval.setSummary(createPaymentSummary(amount, note));
            StaffPaymentRequest saved = paymentRequestRepository.save(payment);
            return toPaymentResponse(saved);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPayrollService.class,
                    "updateSentBackPayment(Long,UpdatePaymentRequest)");
        }
    }

    /**
     * Resubmits staff payroll data and returns the {@code PaymentRequestResponse} result.
     *
     * <p>Delegates to {@code approvalWorkflowService.resubmit(...)}.
     *
     * @param paymentRequestId the payment request id supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code toPaymentResponse(payment)}
     */
    @Transactional
    public PaymentRequestResponse resubmit(Long paymentRequestId, PayrollActionRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffPayrollService.class, "resubmit(Long,PayrollActionRequest)");
        try {
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            StaffPaymentRequest payment = getOwnPayment(paymentRequestId, staff.getId());
            approvalWorkflowService.resubmit(
                    payment.getApprovalRequest().getId(), staff, request.comment());
            return toPaymentResponse(payment);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPayrollService.class,
                    "resubmit(Long,PayrollActionRequest)");
        }
    }

    /**
     * Cancels staff payroll data and returns the {@code PaymentRequestResponse} result.
     *
     * <p>Delegates to {@code approvalWorkflowService.cancelOwnRequest(...)}.
     *
     * @param paymentRequestId the payment request id supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code toPaymentResponse(payment)}
     */
    @Transactional
    public PaymentRequestResponse cancel(Long paymentRequestId, PayrollActionRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffPayrollService.class, "cancel(Long,PayrollActionRequest)");
        try {
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            StaffPaymentRequest payment = getOwnPayment(paymentRequestId, staff.getId());
            approvalWorkflowService.cancelOwnRequest(
                    payment.getApprovalRequest().getId(), staff, request.comment());
            return toPaymentResponse(payment);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPayrollService.class,
                    "cancel(Long,PayrollActionRequest)");
        }
    }

    /**
     * Returns own payment.
     *
     * @param paymentRequestId the payment request id
     * @param staffUserId the staff user id
     * @return the get own payment result
     */
    private StaffPaymentRequest getOwnPayment(Long paymentRequestId, Long staffUserId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffPayrollService.class, "getOwnPayment(Long,Long)");
        try {
            return paymentRequestRepository
                    .findOwnDetailedById(paymentRequestId, staffUserId)
                    .orElseThrow(
                            () -> new IllegalArgumentException("Payment request does not exist."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPayrollService.class,
                    "getOwnPayment(Long,Long)");
        }
    }

    /**
     * Returns accessible branch.
     *
     * @param staff the staff
     * @param branchId the branch id
     * @return the get accessible branch result
     */
    private Branch getAccessibleBranch(StaffUser staff, Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffPayrollService.class, "getAccessibleBranch(StaffUser,Long)");
        try {
            Branch branch =
                    branchRepository
                            .findById(branchId)
                            .orElseThrow(
                                    () -> new IllegalArgumentException("Branch does not exist."));
            boolean allowed =
                    staff.getBranches().stream().anyMatch(item -> item.getId().equals(branchId));
            if (!allowed) {
                throw new AccessDeniedException(
                        "You cannot submit a payment request for a branch outside your access.");
            }
            return branch;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPayrollService.class,
                    "getAccessibleBranch(StaffUser,Long)");
        }
    }

    /**
     * Normalizes positive amount.
     *
     * @param amount the amount
     * @return the normalize positive amount result
     */
    private BigDecimal normalizePositiveAmount(BigDecimal amount) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffPayrollService.class, "normalizePositiveAmount(BigDecimal)");
        try {
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("Payment amount must be greater than zero.");
            }
            return money(amount);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPayrollService.class,
                    "normalizePositiveAmount(BigDecimal)");
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
                MethodTiming.start(StaffPayrollService.class, "money(BigDecimal)");
        try {
            return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffPayrollService.class, "money(BigDecimal)");
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
                MethodTiming.start(StaffPayrollService.class, "normalizeNullable(String)");
        try {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPayrollService.class,
                    "normalizeNullable(String)");
        }
    }

    /**
     * Creates payment summary.
     *
     * @param amount the amount
     * @param note the note
     * @return the create payment summary result
     */
    private String createPaymentSummary(BigDecimal amount, String note) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffPayrollService.class, "createPaymentSummary(BigDecimal,String)");
        try {
            String summary = "Amount taken: ₹" + amount.toPlainString();
            if (note != null) {
                summary += System.lineSeparator() + "Note: " + note;
            }
            return summary;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPayrollService.class,
                    "createPaymentSummary(BigDecimal,String)");
        }
    }

    /**
     * Tos earning response.
     *
     * @param earning the earning
     * @return the to earning response result
     */
    private EarningResponse toEarningResponse(StaffEarning earning) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffPayrollService.class, "toEarningResponse(StaffEarning)");
        try {
            return new EarningResponse(
                    earning.getId(),
                    earning.getAttendance().getId(),
                    earning.getBranch().getId(),
                    earning.getBranch().getName(),
                    earning.getEarningDate(),
                    earning.getAttendanceType(),
                    earning.getRateSnapshot(),
                    earning.getAmount(),
                    earning.getCreatedAt());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPayrollService.class,
                    "toEarningResponse(StaffEarning)");
        }
    }

    /**
     * Tos payment response.
     *
     * @param payment the payment
     * @return the to payment response result
     */
    private PaymentRequestResponse toPaymentResponse(StaffPaymentRequest payment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffPayrollService.class, "toPaymentResponse(StaffPaymentRequest)");
        try {
            ApprovalRequest approval = payment.getApprovalRequest();
            return new PaymentRequestResponse(
                    payment.getId(),
                    approval.getRequestNumber(),
                    approval.getStatus(),
                    approval.getWorkflowVersion(),
                    payment.getBranch().getId(),
                    payment.getBranch().getName(),
                    payment.getAmount(),
                    payment.getNote(),
                    approval.getSubmittedAt(),
                    approval.getResolvedAt(),
                    payment.getCreatedAt(),
                    payment.getUpdatedAt());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPayrollService.class,
                    "toPaymentResponse(StaffPaymentRequest)");
        }
    }
}
