package com.gokulsweets.restaurant.staff.approval;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.config.ApplicationClock;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.staff.StaffUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Coordinates approval workflow operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalWorkflowService {

    private final ApprovalRequestRepository approvalRequestRepository;

    private final ApprovalRequestHistoryRepository historyRepository;

    private final ApplicationClock applicationClock;

    /*
     * =========================================================
     * DOMAIN ENTRY POINT
     * =========================================================
     *
     * Leave / attendance / payroll services call this method
     * after validating their own domain data.
     */
    /**
     * Creates request.
     *
     * @param requestType the request type
     * @param staffUser the staff user
     * @param branch the branch
     * @param title the title
     * @param summary the summary
     * @return the create request result
     */
    @Transactional
    public ApprovalRequest createRequest(
            ApprovalRequestType requestType,
            StaffUser staffUser,
            Branch branch,
            String title,
            String summary) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ApprovalWorkflowService.class,
                        "createRequest(ApprovalRequestType,StaffUser,Branch,String,String)");
        try {
            if (requestType == null) {
                throw new IllegalArgumentException("Approval request type is required.");
            }
            if (staffUser == null) {
                throw new IllegalArgumentException("Staff user is required.");
            }
            if (branch == null) {
                throw new IllegalArgumentException("Branch is required.");
            }
            String normalizedTitle = normalizeRequired(title, "Approval title");
            String normalizedSummary = normalizeNullable(summary);
            LocalDateTime now = applicationClock.now();
            ApprovalRequest request = new ApprovalRequest();
            request.setRequestType(requestType);
            request.setStaffUser(staffUser);
            request.setBranch(branch);
            request.setStatus(ApprovalRequestStatus.PENDING);
            request.setTitle(normalizedTitle);
            request.setSummary(normalizedSummary);
            request.setWorkflowVersion(1);
            request.setSubmittedAt(now);
            ApprovalRequest saved = approvalRequestRepository.saveAndFlush(request);
            saved.setRequestNumber("APR-%08d".formatted(saved.getId()));
            saved = approvalRequestRepository.save(saved);
            appendHistory(
                    saved,
                    ApprovalRequestAction.SUBMITTED,
                    null,
                    ApprovalRequestStatus.PENDING,
                    staffUser,
                    null);
            log.info(
                    "Approval request created: approvalRequestId={}, requestNumber={}, type={},"
                            + " staffUserId={}, branchId={}",
                    saved.getId(),
                    saved.getRequestNumber(),
                    saved.getRequestType(),
                    staffUser.getId(),
                    branch.getId());
            return saved;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ApprovalWorkflowService.class,
                    "createRequest(ApprovalRequestType,StaffUser,Branch,String,String)");
        }
    }

    /*
     * =========================================================
     * DOMAIN AUTO APPROVAL
     * =========================================================
     *
     * Used only after the owning domain has positively decided
     * that the request qualifies for automatic approval.
     *
     * Current attendance rule:
     * same-day PRESENT self-attendance can be auto approved.
     */
    /**
     * Autos approve.
     *
     * @param approvalRequestId the approval request id
     * @param requestingStaff the requesting staff
     * @param comment the comment
     * @return the auto approve result
     */
    @Transactional
    public ApprovalRequest autoApprove(
            Long approvalRequestId, StaffUser requestingStaff, String comment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ApprovalWorkflowService.class, "autoApprove(Long,StaffUser,String)");
        try {
            if (requestingStaff == null) {
                throw new IllegalArgumentException("Staff user is required.");
            }
            ApprovalRequest request =
                    approvalRequestRepository
                            .findByIdForUpdate(approvalRequestId)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Approval request does not exist."));
            if (!request.getStaffUser().getId().equals(requestingStaff.getId())) {
                throw new IllegalArgumentException(
                        "Automatic approval can only be applied to the staff member's own"
                                + " request.");
            }
            if (request.getStatus() != ApprovalRequestStatus.PENDING) {
                throw new IllegalStateException(
                        "Only a pending request can be automatically approved.");
            }
            ApprovalRequestStatus fromStatus = request.getStatus();
            request.setStatus(ApprovalRequestStatus.APPROVED);
            request.setResolvedAt(applicationClock.now());
            ApprovalRequest saved = approvalRequestRepository.save(request);
            appendHistory(
                    saved,
                    ApprovalRequestAction.APPROVED,
                    fromStatus,
                    ApprovalRequestStatus.APPROVED,
                    requestingStaff,
                    comment);
            log.info(
                    "Approval request automatically approved: approvalRequestId={},"
                            + " requestNumber={}, type={}, staffUserId={}",
                    saved.getId(),
                    saved.getRequestNumber(),
                    saved.getRequestType(),
                    requestingStaff.getId());
            return saved;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ApprovalWorkflowService.class,
                    "autoApprove(Long,StaffUser,String)");
        }
    }

    /*
     * =========================================================
     * RESUBMIT SENT-BACK REQUEST
     * =========================================================
     *
     * Domain services should update their own draft first and
     * then call this method.
     */
    /**
     * Resubmits approval workflow data and returns the {@code ApprovalRequest} result.
     *
     * <p>Delegates to {@code approvalRequestRepository.findByIdForUpdate(...)}, {@code
     * approvalRequestRepository.save(...)}.
     *
     * @param approvalRequestId the approval request id supplied to this method
     * @param requestingStaff the requesting staff supplied to this method
     * @param comment the comment supplied to this method
     * @return the value of {@code saved}
     * @throws IllegalArgumentException when the method rejects the request with {@code You can only
     *     resubmit your own request.}
     * @throws IllegalStateException when the method rejects the request with {@code Only a
     *     sent-back request can be resubmitted.}
     */
    @Transactional
    public ApprovalRequest resubmit(
            Long approvalRequestId, StaffUser requestingStaff, String comment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ApprovalWorkflowService.class, "resubmit(Long,StaffUser,String)");
        try {
            ApprovalRequest request =
                    approvalRequestRepository
                            .findByIdForUpdate(approvalRequestId)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Approval request does not exist."));
            if (!request.getStaffUser().getId().equals(requestingStaff.getId())) {
                throw new IllegalArgumentException("You can only resubmit your own request.");
            }
            if (request.getStatus() != ApprovalRequestStatus.SENT_BACK) {
                throw new IllegalStateException("Only a sent-back request can be resubmitted.");
            }
            ApprovalRequestStatus fromStatus = request.getStatus();
            request.setStatus(ApprovalRequestStatus.PENDING);
            request.setWorkflowVersion(request.getWorkflowVersion() + 1);
            request.setSubmittedAt(applicationClock.now());
            request.setResolvedAt(null);
            ApprovalRequest saved = approvalRequestRepository.save(request);
            appendHistory(
                    saved,
                    ApprovalRequestAction.RESUBMITTED,
                    fromStatus,
                    ApprovalRequestStatus.PENDING,
                    requestingStaff,
                    comment);
            return saved;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ApprovalWorkflowService.class,
                    "resubmit(Long,StaffUser,String)");
        }
    }

    /**
     * Cancels own request.
     *
     * @param approvalRequestId the approval request id
     * @param requestingStaff the requesting staff
     * @param comment the comment
     * @return the cancel own request result
     */
    @Transactional
    public ApprovalRequest cancelOwnRequest(
            Long approvalRequestId, StaffUser requestingStaff, String comment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ApprovalWorkflowService.class, "cancelOwnRequest(Long,StaffUser,String)");
        try {
            ApprovalRequest request =
                    approvalRequestRepository
                            .findByIdForUpdate(approvalRequestId)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Approval request does not exist."));
            if (!request.getStaffUser().getId().equals(requestingStaff.getId())) {
                throw new IllegalArgumentException("You can only cancel your own request.");
            }
            if (request.getStatus() != ApprovalRequestStatus.PENDING
                    && request.getStatus() != ApprovalRequestStatus.SENT_BACK) {
                throw new IllegalStateException("This request can no longer be cancelled.");
            }
            ApprovalRequestStatus fromStatus = request.getStatus();
            request.setStatus(ApprovalRequestStatus.CANCELLED);
            request.setResolvedAt(applicationClock.now());
            ApprovalRequest saved = approvalRequestRepository.save(request);
            appendHistory(
                    saved,
                    ApprovalRequestAction.CANCELLED,
                    fromStatus,
                    ApprovalRequestStatus.CANCELLED,
                    requestingStaff,
                    comment);
            return saved;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ApprovalWorkflowService.class,
                    "cancelOwnRequest(Long,StaffUser,String)");
        }
    }

    /*
     * =========================================================
     * HISTORY
     * =========================================================
     */
    /**
     * Appends history.
     *
     * @param request the request
     * @param action the action
     * @param fromStatus the from status
     * @param toStatus the to status
     * @param actor the actor
     * @param comment the comment
     */
    public void appendHistory(
            ApprovalRequest request,
            ApprovalRequestAction action,
            ApprovalRequestStatus fromStatus,
            ApprovalRequestStatus toStatus,
            StaffUser actor,
            String comment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ApprovalWorkflowService.class,
                        "appendHistory(ApprovalRequest,ApprovalRequestAction,ApprovalRequestStatus,ApprovalRequestStatus,StaffUser,String)");
        try {
            ApprovalRequestHistory history = new ApprovalRequestHistory();
            history.setApprovalRequest(request);
            history.setAction(action);
            history.setFromStatus(fromStatus);
            history.setToStatus(toStatus);
            history.setActorStaffUser(actor);
            history.setActorName(actor.getFullName());
            history.setComment(normalizeNullable(comment));
            historyRepository.save(history);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ApprovalWorkflowService.class,
                    "appendHistory(ApprovalRequest,ApprovalRequestAction,ApprovalRequestStatus,ApprovalRequestStatus,StaffUser,String)");
        }
    }

    /**
     * Normalizes required.
     *
     * @param value the value
     * @param fieldName the field name
     * @return the normalize required result
     */
    private String normalizeRequired(String value, String fieldName) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ApprovalWorkflowService.class, "normalizeRequired(String,String)");
        try {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " is required.");
            }
            return value.trim();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ApprovalWorkflowService.class,
                    "normalizeRequired(String,String)");
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
                MethodTiming.start(ApprovalWorkflowService.class, "normalizeNullable(String)");
        try {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ApprovalWorkflowService.class,
                    "normalizeNullable(String)");
        }
    }
}
