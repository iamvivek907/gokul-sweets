package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branch.BranchRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffPayrollService {

    private static final EnumSet<ApprovalRequestStatus>
            COMMITTED_STATUSES =
            EnumSet.of(
                    ApprovalRequestStatus.PENDING,
                    ApprovalRequestStatus.APPROVED,
                    ApprovalRequestStatus.SENT_BACK
            );

    private final StaffAuthorizationService
            staffAuthorizationService;

    private final BranchRepository
            branchRepository;

    private final PayrollStaffLockRepository
            staffLockRepository;

    private final StaffEarningRepository
            earningRepository;

    private final StaffPaymentRequestRepository
            paymentRequestRepository;

    private final PayrollEarningSyncService
            earningSyncService;

    private final PayrollBalanceService
            payrollBalanceService;

    private final ApprovalWorkflowService
            approvalWorkflowService;


    @Transactional(readOnly = true)
    public PayrollOptionsResponse getOptions() {

        StaffUser staff =
                staffAuthorizationService
                        .getCurrentStaff();

        List<PayrollBranchOptionResponse> branches =
                staff.getBranches()
                        .stream()
                        .sorted(
                                java.util.Comparator
                                        .comparing(
                                                Branch::getName,
                                                String.CASE_INSENSITIVE_ORDER
                                        )
                                        .thenComparing(
                                                Branch::getId
                                        )
                        )
                        .map(
                                branch ->
                                        new PayrollBranchOptionResponse(
                                                branch.getId(),
                                                branch.getCode(),
                                                branch.getName(),
                                                branch.isActive()
                                        )
                        )
                        .toList();

        return new PayrollOptionsResponse(
                branches
        );
    }


    @Transactional
    public PayrollSummaryResponse getMySummary() {

        StaffUser current =
                staffAuthorizationService
                        .getCurrentStaff();

        StaffUser locked =
                staffLockRepository
                        .findByIdForUpdate(
                                current.getId()
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


    @Transactional
    public Page<EarningResponse> getMyEarnings(
            int page,
            int size
    ) {

        StaffUser current =
                staffAuthorizationService
                        .getCurrentStaff();

        StaffUser staff =
                staffLockRepository
                        .findByIdForUpdate(
                                current.getId()
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Staff user does not exist."
                                        )
                        );

        earningSyncService
                .syncApprovedAttendance(
                        staff.getId()
                );

        return earningRepository
                .findByStaffUserId(
                        staff.getId(),
                        PageRequest.of(
                                Math.max(
                                        page,
                                        0
                                ),
                                Math.min(
                                        Math.max(
                                                size,
                                                1
                                        ),
                                        100
                                ),
                                Sort.by(
                                        Sort.Direction.DESC,
                                        "earningDate"
                                ).and(
                                        Sort.by(
                                                Sort.Direction.DESC,
                                                "id"
                                        )
                                )
                        )
                )
                .map(
                        this::toEarningResponse
                );
    }


    @Transactional(readOnly = true)
    public Page<PaymentRequestResponse> getMyPaymentRequests(
            int page,
            int size
    ) {

        StaffUser staff =
                staffAuthorizationService
                        .getCurrentStaff();

        return paymentRequestRepository
                .findByStaffUserId(
                        staff.getId(),
                        PageRequest.of(
                                Math.max(
                                        page,
                                        0
                                ),
                                Math.min(
                                        Math.max(
                                                size,
                                                1
                                        ),
                                        100
                                ),
                                Sort.by(
                                        Sort.Direction.DESC,
                                        "createdAt"
                                ).and(
                                        Sort.by(
                                                Sort.Direction.DESC,
                                                "id"
                                        )
                                )
                        )
                )
                .map(
                        this::toPaymentResponse
                );
    }


    @Transactional
    public PaymentRequestResponse createPaymentRequest(
            CreatePaymentRequest request
    ) {

        StaffUser current =
                staffAuthorizationService
                        .getCurrentStaff();

        StaffUser staff =
                staffLockRepository
                        .findByIdForUpdate(
                                current.getId()
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Staff user does not exist."
                                        )
                        );

        Branch branch =
                getAccessibleBranch(
                        current,
                        request.branchId()
                );

        earningSyncService
                .syncApprovedAttendance(
                        staff.getId()
                );

        BigDecimal amount =
                normalizePositiveAmount(
                        request.amount()
                );

        BigDecimal available =
                payrollBalanceService
                        .calculateAvailable(
                                staff.getId(),
                                null
                        );

        if (
                amount.compareTo(
                        available
                ) > 0
        ) {

            throw new IllegalStateException(
                    "Requested amount exceeds currently available earned balance."
            );
        }

        String note =
                normalizeNullable(
                        request.note()
                );

        ApprovalRequest approval =
                approvalWorkflowService
                        .createRequest(
                                ApprovalRequestType.PAYMENT,
                                staff,
                                branch,
                                "Money taken request - ₹"
                                        + amount.toPlainString(),
                                createPaymentSummary(
                                        amount,
                                        note
                                )
                        );

        StaffPaymentRequest payment =
                new StaffPaymentRequest();

        payment.setStaffUser(
                staff
        );

        payment.setBranch(
                branch
        );

        payment.setApprovalRequest(
                approval
        );

        payment.setAmount(
                amount
        );

        payment.setNote(
                note
        );

        StaffPaymentRequest saved =
                paymentRequestRepository
                        .save(
                                payment
                        );

        log.info(
                "Staff payment request created: paymentRequestId={}, approvalRequestId={}, staffUserId={}, amount={}",
                saved.getId(),
                approval.getId(),
                staff.getId(),
                saved.getAmount()
        );

        return toPaymentResponse(
                saved
        );
    }


    @Transactional
    public PaymentRequestResponse updateSentBackPayment(
            Long paymentRequestId,
            UpdatePaymentRequest request
    ) {

        StaffUser current =
                staffAuthorizationService
                        .getCurrentStaff();

        StaffUser staff =
                staffLockRepository
                        .findByIdForUpdate(
                                current.getId()
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Staff user does not exist."
                                        )
                        );

        StaffPaymentRequest payment =
                getOwnPayment(
                        paymentRequestId,
                        staff.getId()
                );

        if (
                payment.getApprovalRequest()
                        .getStatus()
                        != ApprovalRequestStatus.SENT_BACK
        ) {

            throw new IllegalStateException(
                    "Only a sent-back payment request can be corrected."
            );
        }

        Branch branch =
                getAccessibleBranch(
                        current,
                        request.branchId()
                );

        earningSyncService
                .syncApprovedAttendance(
                        staff.getId()
                );

        BigDecimal amount =
                normalizePositiveAmount(
                        request.amount()
                );

        BigDecimal availableExcludingCurrent =
                payrollBalanceService
                        .calculateAvailable(
                                staff.getId(),
                                paymentRequestId
                        );

        if (
                amount.compareTo(
                        availableExcludingCurrent
                ) > 0
        ) {

            throw new IllegalStateException(
                    "Corrected amount exceeds currently available earned balance."
            );
        }

        String note =
                normalizeNullable(
                        request.note()
                );

        payment.setBranch(
                branch
        );

        payment.setAmount(
                amount
        );

        payment.setNote(
                note
        );

        ApprovalRequest approval =
                payment.getApprovalRequest();

        approval.setBranch(
                branch
        );

        approval.setTitle(
                "Money taken request - ₹"
                        + amount.toPlainString()
        );

        approval.setSummary(
                createPaymentSummary(
                        amount,
                        note
                )
        );

        StaffPaymentRequest saved =
                paymentRequestRepository
                        .save(
                                payment
                        );

        return toPaymentResponse(
                saved
        );
    }


    @Transactional
    public PaymentRequestResponse resubmit(
            Long paymentRequestId,
            PayrollActionRequest request
    ) {

        StaffUser staff =
                staffAuthorizationService
                        .getCurrentStaff();

        StaffPaymentRequest payment =
                getOwnPayment(
                        paymentRequestId,
                        staff.getId()
                );

        approvalWorkflowService
                .resubmit(
                        payment.getApprovalRequest()
                                .getId(),
                        staff,
                        request.comment()
                );

        return toPaymentResponse(
                payment
        );
    }


    @Transactional
    public PaymentRequestResponse cancel(
            Long paymentRequestId,
            PayrollActionRequest request
    ) {

        StaffUser staff =
                staffAuthorizationService
                        .getCurrentStaff();

        StaffPaymentRequest payment =
                getOwnPayment(
                        paymentRequestId,
                        staff.getId()
                );

        approvalWorkflowService
                .cancelOwnRequest(
                        payment.getApprovalRequest()
                                .getId(),
                        staff,
                        request.comment()
                );

        return toPaymentResponse(
                payment
        );
    }


    private StaffPaymentRequest getOwnPayment(
            Long paymentRequestId,
            Long staffUserId
    ) {

        return paymentRequestRepository
                .findOwnDetailedById(
                        paymentRequestId,
                        staffUserId
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Payment request does not exist."
                                )
                );
    }


    private Branch getAccessibleBranch(
            StaffUser staff,
            Long branchId
    ) {

        Branch branch =
                branchRepository
                        .findById(
                                branchId
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Branch does not exist."
                                        )
                        );

        boolean allowed =
                staff.getBranches()
                        .stream()
                        .anyMatch(
                                item ->
                                        item.getId()
                                                .equals(
                                                        branchId
                                                )
                        );

        if (!allowed) {

            throw new AccessDeniedException(
                    "You cannot submit a payment request for a branch outside your access."
            );
        }

        return branch;
    }


    private BigDecimal normalizePositiveAmount(
            BigDecimal amount
    ) {

        if (
                amount == null
                        ||
                        amount.signum() <= 0
        ) {

            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero."
            );
        }

        return money(
                amount
        );
    }


    private BigDecimal money(
            BigDecimal value
    ) {

        return (
                value == null
                        ? BigDecimal.ZERO
                        : value
        ).setScale(
                2,
                RoundingMode.HALF_UP
        );
    }


    private String normalizeNullable(
            String value
    ) {

        if (
                value == null
                        ||
                        value.isBlank()
        ) {

            return null;
        }

        return value.trim();
    }


    private String createPaymentSummary(
            BigDecimal amount,
            String note
    ) {

        String summary =
                "Amount taken: ₹"
                        + amount.toPlainString();

        if (
                note != null
        ) {

            summary +=
                    System.lineSeparator()
                            + "Note: "
                            + note;
        }

        return summary;
    }


    private EarningResponse toEarningResponse(
            StaffEarning earning
    ) {

        return new EarningResponse(
                earning.getId(),
                earning.getAttendance()
                        .getId(),
                earning.getBranch()
                        .getId(),
                earning.getBranch()
                        .getName(),
                earning.getEarningDate(),
                earning.getAttendanceType(),
                earning.getRateSnapshot(),
                earning.getAmount(),
                earning.getCreatedAt()
        );
    }


    private PaymentRequestResponse toPaymentResponse(
            StaffPaymentRequest payment
    ) {

        ApprovalRequest approval =
                payment.getApprovalRequest();

        return new PaymentRequestResponse(
                payment.getId(),
                approval.getRequestNumber(),
                approval.getStatus(),
                approval.getWorkflowVersion(),
                payment.getBranch()
                        .getId(),
                payment.getBranch()
                        .getName(),
                payment.getAmount(),
                payment.getNote(),
                approval.getSubmittedAt(),
                approval.getResolvedAt(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
