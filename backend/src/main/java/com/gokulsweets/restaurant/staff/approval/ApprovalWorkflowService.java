package com.gokulsweets.restaurant.staff.approval;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.config.ApplicationClock;
import com.gokulsweets.restaurant.staff.StaffUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalWorkflowService {

    private final ApprovalRequestRepository
            approvalRequestRepository;

    private final ApprovalRequestHistoryRepository
            historyRepository;

    private final ApplicationClock  applicationClock;


    /*
     * =========================================================
     * DOMAIN ENTRY POINT
     * =========================================================
     *
     * Leave / attendance / payroll services call this method
     * after validating their own domain data.
     */

    @Transactional
    public ApprovalRequest createRequest(
            ApprovalRequestType requestType,
            StaffUser staffUser,
            Branch branch,
            String title,
            String summary
    ) {

        if (requestType == null) {
            throw new IllegalArgumentException(
                    "Approval request type is required."
            );
        }

        if (staffUser == null) {
            throw new IllegalArgumentException(
                    "Staff user is required."
            );
        }

        if (branch == null) {
            throw new IllegalArgumentException(
                    "Branch is required."
            );
        }

        String normalizedTitle =
                normalizeRequired(
                        title,
                        "Approval title"
                );

        String normalizedSummary =
                normalizeNullable(
                        summary
                );

        LocalDateTime now = applicationClock.now();

        ApprovalRequest request =
                new ApprovalRequest();

        request.setRequestType(
                requestType
        );

        request.setStaffUser(
                staffUser
        );

        request.setBranch(
                branch
        );

        request.setStatus(
                ApprovalRequestStatus.PENDING
        );

        request.setTitle(
                normalizedTitle
        );

        request.setSummary(
                normalizedSummary
        );

        request.setWorkflowVersion(
                1
        );

        request.setSubmittedAt(
                now
        );

        ApprovalRequest saved =
                approvalRequestRepository
                        .saveAndFlush(
                                request
                        );

        saved.setRequestNumber(
                "APR-%08d".formatted(
                        saved.getId()
                )
        );

        saved =
                approvalRequestRepository
                        .save(
                                saved
                        );

        appendHistory(
                saved,
                ApprovalRequestAction.SUBMITTED,
                null,
                ApprovalRequestStatus.PENDING,
                staffUser,
                null
        );

        log.info(
                "Approval request created: approvalRequestId={}, requestNumber={}, type={}, staffUserId={}, branchId={}",
                saved.getId(),
                saved.getRequestNumber(),
                saved.getRequestType(),
                staffUser.getId(),
                branch.getId()
        );

        return saved;
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

    @Transactional
    public ApprovalRequest autoApprove(
            Long approvalRequestId,
            StaffUser requestingStaff,
            String comment
    ) {

        if (requestingStaff == null) {

            throw new IllegalArgumentException(
                    "Staff user is required."
            );
        }

        ApprovalRequest request =
                approvalRequestRepository
                        .findByIdForUpdate(
                                approvalRequestId
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Approval request does not exist."
                                        )
                        );

        if (
                !request.getStaffUser()
                        .getId()
                        .equals(
                                requestingStaff.getId()
                        )
        ) {

            throw new IllegalArgumentException(
                    "Automatic approval can only be applied to the staff member's own request."
            );
        }

        if (
                request.getStatus()
                        != ApprovalRequestStatus.PENDING
        ) {

            throw new IllegalStateException(
                    "Only a pending request can be automatically approved."
            );
        }

        ApprovalRequestStatus fromStatus =
                request.getStatus();

        request.setStatus(
                ApprovalRequestStatus.APPROVED
        );

        request.setResolvedAt(
                applicationClock.now()
        );

        ApprovalRequest saved =
                approvalRequestRepository
                        .save(
                                request
                        );

        appendHistory(
                saved,
                ApprovalRequestAction.APPROVED,
                fromStatus,
                ApprovalRequestStatus.APPROVED,
                requestingStaff,
                comment
        );

        log.info(
                "Approval request automatically approved: approvalRequestId={}, requestNumber={}, type={}, staffUserId={}",
                saved.getId(),
                saved.getRequestNumber(),
                saved.getRequestType(),
                requestingStaff.getId()
        );

        return saved;
    }


    /*
     * =========================================================
     * RESUBMIT SENT-BACK REQUEST
     * =========================================================
     *
     * Domain services should update their own draft first and
     * then call this method.
     */

    @Transactional
    public ApprovalRequest resubmit(
            Long approvalRequestId,
            StaffUser requestingStaff,
            String comment
    ) {

        ApprovalRequest request =
                approvalRequestRepository
                        .findByIdForUpdate(
                                approvalRequestId
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Approval request does not exist."
                                        )
                        );

        if (
                !request.getStaffUser()
                        .getId()
                        .equals(
                                requestingStaff.getId()
                        )
        ) {

            throw new IllegalArgumentException(
                    "You can only resubmit your own request."
            );
        }

        if (
                request.getStatus()
                        != ApprovalRequestStatus.SENT_BACK
        ) {

            throw new IllegalStateException(
                    "Only a sent-back request can be resubmitted."
            );
        }

        ApprovalRequestStatus fromStatus =
                request.getStatus();

        request.setStatus(
                ApprovalRequestStatus.PENDING
        );

        request.setWorkflowVersion(
                request.getWorkflowVersion() + 1
        );

        request.setSubmittedAt(
                applicationClock.now()
        );

        request.setResolvedAt(
                null
        );

        ApprovalRequest saved =
                approvalRequestRepository.save(
                        request
                );

        appendHistory(
                saved,
                ApprovalRequestAction.RESUBMITTED,
                fromStatus,
                ApprovalRequestStatus.PENDING,
                requestingStaff,
                comment
        );

        return saved;
    }


    @Transactional
    public ApprovalRequest cancelOwnRequest(
            Long approvalRequestId,
            StaffUser requestingStaff,
            String comment
    ) {

        ApprovalRequest request =
                approvalRequestRepository
                        .findByIdForUpdate(
                                approvalRequestId
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Approval request does not exist."
                                        )
                        );

        if (
                !request.getStaffUser()
                        .getId()
                        .equals(
                                requestingStaff.getId()
                        )
        ) {

            throw new IllegalArgumentException(
                    "You can only cancel your own request."
            );
        }

        if (
                request.getStatus()
                        != ApprovalRequestStatus.PENDING
                        &&
                        request.getStatus()
                                != ApprovalRequestStatus.SENT_BACK
        ) {

            throw new IllegalStateException(
                    "This request can no longer be cancelled."
            );
        }

        ApprovalRequestStatus fromStatus =
                request.getStatus();

        request.setStatus(
                ApprovalRequestStatus.CANCELLED
        );

        request.setResolvedAt(
                applicationClock.now()
        );

        ApprovalRequest saved =
                approvalRequestRepository.save(
                        request
                );

        appendHistory(
                saved,
                ApprovalRequestAction.CANCELLED,
                fromStatus,
                ApprovalRequestStatus.CANCELLED,
                requestingStaff,
                comment
        );

        return saved;
    }


    /*
     * =========================================================
     * HISTORY
     * =========================================================
     */

    public void appendHistory(
            ApprovalRequest request,
            ApprovalRequestAction action,
            ApprovalRequestStatus fromStatus,
            ApprovalRequestStatus toStatus,
            StaffUser actor,
            String comment
    ) {

        ApprovalRequestHistory history =
                new ApprovalRequestHistory();

        history.setApprovalRequest(
                request
        );

        history.setAction(
                action
        );

        history.setFromStatus(
                fromStatus
        );

        history.setToStatus(
                toStatus
        );

        history.setActorStaffUser(
                actor
        );

        history.setActorName(
                actor.getFullName()
        );

        history.setComment(
                normalizeNullable(
                        comment
                )
        );

        historyRepository.save(
                history
        );
    }


    private String normalizeRequired(
            String value,
            String fieldName
    ) {

        if (
                value == null
                        ||
                        value.isBlank()
        ) {

            throw new IllegalArgumentException(
                    fieldName + " is required."
            );
        }

        return value.trim();
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
}
