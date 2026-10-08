package com.gokulsweets.restaurant.staff.approval;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.staff.approval.dto.ApprovalActionRequest;
import com.gokulsweets.restaurant.staff.approval.dto.ApprovalCountsResponse;
import com.gokulsweets.restaurant.staff.approval.dto.ApprovalHistoryResponse;
import com.gokulsweets.restaurant.staff.approval.dto.ApprovalOptionsResponse;
import com.gokulsweets.restaurant.staff.approval.dto.ApprovalRequestDetailResponse;
import com.gokulsweets.restaurant.staff.approval.dto.ApprovalRequestResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Coordinates admin approval operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminApprovalService {

    private static final int MAX_PAGE_SIZE = AppConstant.ADMIN_APPROVAL_SERVICE_MAX_PAGE_SIZE;

    private final ApprovalRequestRepository approvalRequestRepository;

    private final ApprovalRequestHistoryRepository historyRepository;

    private final ApprovalWorkflowService approvalWorkflowService;

    private final BranchRepository branchRepository;

    private final StaffAuthorizationService staffAuthorizationService;

    /**
     * Returns approvals.
     *
     * @param branchId the branch id
     * @param requestType the request type
     * @param status the status
     * @param page the page
     * @param size the size
     * @return the get approvals result
     */
    @Transactional(readOnly = true)
    public Page<ApprovalRequestResponse> getApprovals(
            Long branchId,
            ApprovalRequestType requestType,
            ApprovalRequestStatus status,
            int page,
            int size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminApprovalService.class,
                        "getApprovals(Long,ApprovalRequestType,ApprovalRequestStatus,int,int)");
        try {
            requireViewPermission();
            StaffUser actor = staffAuthorizationService.getCurrentStaff();
            validateRequestedBranch(actor, branchId);
            Pageable pageable =
                    PageRequest.of(
                            Math.max(page, 0),
                            Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                            Sort.by(Sort.Direction.DESC, "submittedAt")
                                    .and(Sort.by(Sort.Direction.DESC, "id")));
            Specification<ApprovalRequest> specification =
                    buildSpecification(actor, branchId, requestType, status);
            return approvalRequestRepository.findAll(specification, pageable).map(this::toResponse);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminApprovalService.class,
                    "getApprovals(Long,ApprovalRequestType,ApprovalRequestStatus,int,int)");
        }
    }

    /**
     * Returns counts.
     *
     * @param branchId the branch id
     * @param requestType the request type
     * @return the get counts result
     */
    @Transactional(readOnly = true)
    public ApprovalCountsResponse getCounts(Long branchId, ApprovalRequestType requestType) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminApprovalService.class, "getCounts(Long,ApprovalRequestType)");
        try {
            requireViewPermission();
            StaffUser actor = staffAuthorizationService.getCurrentStaff();
            validateRequestedBranch(actor, branchId);
            Specification<ApprovalRequest> base =
                    buildSpecification(actor, branchId, requestType, null);
            long pending =
                    approvalRequestRepository.count(
                            base.and(statusIs(ApprovalRequestStatus.PENDING)));
            long sentBack =
                    approvalRequestRepository.count(
                            base.and(statusIs(ApprovalRequestStatus.SENT_BACK)));
            long approved =
                    approvalRequestRepository.count(
                            base.and(statusIs(ApprovalRequestStatus.APPROVED)));
            long rejected =
                    approvalRequestRepository.count(
                            base.and(statusIs(ApprovalRequestStatus.REJECTED)));
            long cancelled =
                    approvalRequestRepository.count(
                            base.and(statusIs(ApprovalRequestStatus.CANCELLED)));
            return new ApprovalCountsResponse(
                    pending,
                    sentBack,
                    approved,
                    rejected,
                    cancelled,
                    pending + sentBack + approved + rejected + cancelled);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminApprovalService.class,
                    "getCounts(Long,ApprovalRequestType)");
        }
    }

    /**
     * Returns options.
     *
     * @return the get options result
     */
    @Transactional(readOnly = true)
    public ApprovalOptionsResponse getOptions() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminApprovalService.class, "getOptions()");
        try {
            requireViewPermission();
            StaffUser actor = staffAuthorizationService.getCurrentStaff();
            List<ApprovalOptionsResponse.BranchOption> branches =
                    (isOwner(actor)
                                    ? branchRepository.findAll()
                                    : actor.getBranches().stream().toList())
                            .stream()
                                    .sorted(
                                            java.util.Comparator.comparing(
                                                            Branch::getName,
                                                            String.CASE_INSENSITIVE_ORDER)
                                                    .thenComparing(Branch::getId))
                                    .map(
                                            branch ->
                                                    new ApprovalOptionsResponse.BranchOption(
                                                            branch.getId(),
                                                            branch.getCode(),
                                                            branch.getName(),
                                                            branch.isActive()))
                                    .toList();
            return new ApprovalOptionsResponse(
                    branches,
                    List.of(ApprovalRequestType.values()),
                    List.of(ApprovalRequestStatus.values()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminApprovalService.class, "getOptions()");
        }
    }

    /**
     * Returns approval.
     *
     * @param approvalRequestId the approval request id
     * @return the get approval result
     */
    @Transactional(readOnly = true)
    public ApprovalRequestDetailResponse getApproval(Long approvalRequestId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminApprovalService.class, "getApproval(Long)");
        try {
            requireViewPermission();
            ApprovalRequest request =
                    approvalRequestRepository
                            .findById(approvalRequestId)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Approval request does not exist."));
            staffAuthorizationService.requireBranchAccess(request.getBranch().getId());
            var history =
                    historyRepository
                            .findByApprovalRequestIdOrderByCreatedAtAscIdAsc(approvalRequestId)
                            .stream()
                            .map(
                                    item ->
                                            new ApprovalHistoryResponse(
                                                    item.getId(),
                                                    item.getAction(),
                                                    item.getFromStatus(),
                                                    item.getToStatus(),
                                                    item.getActorStaffUser().getId(),
                                                    item.getActorName(),
                                                    item.getComment(),
                                                    item.getCreatedAt()))
                            .toList();
            return new ApprovalRequestDetailResponse(toResponse(request), history);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminApprovalService.class, "getApproval(Long)");
        }
    }

    /**
     * Approves the operation.
     *
     * @param approvalRequestId the approval request id
     * @param actionRequest the action request
     * @return the approve result
     */
    @Transactional
    public ApprovalRequestResponse approve(
            Long approvalRequestId, ApprovalActionRequest actionRequest) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminApprovalService.class, "approve(Long,ApprovalActionRequest)");
        try {
            return transitionPending(
                    approvalRequestId,
                    ApprovalRequestStatus.APPROVED,
                    ApprovalRequestAction.APPROVED,
                    actionRequest.comment());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminApprovalService.class,
                    "approve(Long,ApprovalActionRequest)");
        }
    }

    /**
     * Rejects the operation.
     *
     * @param approvalRequestId the approval request id
     * @param actionRequest the action request
     * @return the reject result
     */
    @Transactional
    public ApprovalRequestResponse reject(
            Long approvalRequestId, ApprovalActionRequest actionRequest) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminApprovalService.class, "reject(Long,ApprovalActionRequest)");
        try {
            String comment =
                    normalizeRequiredComment(
                            actionRequest.comment(), "A rejection reason is required.");
            return transitionPending(
                    approvalRequestId,
                    ApprovalRequestStatus.REJECTED,
                    ApprovalRequestAction.REJECTED,
                    comment);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminApprovalService.class,
                    "reject(Long,ApprovalActionRequest)");
        }
    }

    /**
     * Sends back.
     *
     * @param approvalRequestId the approval request id
     * @param actionRequest the action request
     * @return the send back result
     */
    @Transactional
    public ApprovalRequestResponse sendBack(
            Long approvalRequestId, ApprovalActionRequest actionRequest) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminApprovalService.class, "sendBack(Long,ApprovalActionRequest)");
        try {
            String comment =
                    normalizeRequiredComment(
                            actionRequest.comment(),
                            "A correction note is required when sending a request back.");
            return transitionPending(
                    approvalRequestId,
                    ApprovalRequestStatus.SENT_BACK,
                    ApprovalRequestAction.SENT_BACK,
                    comment);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminApprovalService.class,
                    "sendBack(Long,ApprovalActionRequest)");
        }
    }

    /**
     * Transitions pending.
     *
     * @param approvalRequestId the approval request id
     * @param targetStatus the target status
     * @param action the action
     * @param comment the comment
     * @return the transition pending result
     */
    private ApprovalRequestResponse transitionPending(
            Long approvalRequestId,
            ApprovalRequestStatus targetStatus,
            ApprovalRequestAction action,
            String comment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminApprovalService.class,
                        "transitionPending(Long,ApprovalRequestStatus,ApprovalRequestAction,String)");
        try {
            requireManagePermission();
            StaffUser actor = staffAuthorizationService.getCurrentStaff();
            ApprovalRequest request =
                    approvalRequestRepository
                            .findByIdForUpdate(approvalRequestId)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Approval request does not exist."));
            staffAuthorizationService.requireBranchAccess(request.getBranch().getId());
            if (request.getStatus() != ApprovalRequestStatus.PENDING) {
                throw new IllegalStateException("Only a pending request can be actioned.");
            }
            ApprovalRequestStatus fromStatus = request.getStatus();
            request.setStatus(targetStatus);
            request.setResolvedAt(LocalDateTime.now());
            ApprovalRequest saved = approvalRequestRepository.save(request);
            approvalWorkflowService.appendHistory(
                    saved, action, fromStatus, targetStatus, actor, comment);
            log.info(
                    "Approval request actioned: approvalRequestId={}, requestNumber={}, action={},"
                            + " actedByStaffUserId={}",
                    saved.getId(),
                    saved.getRequestNumber(),
                    action,
                    actor.getId());
            return toResponse(saved);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminApprovalService.class,
                    "transitionPending(Long,ApprovalRequestStatus,ApprovalRequestAction,String)");
        }
    }

    /**
     * Builds specification.
     *
     * @param actor the actor
     * @param branchId the branch id
     * @param requestType the request type
     * @param status the status
     * @return the build specification result
     */
    private Specification<ApprovalRequest> buildSpecification(
            StaffUser actor,
            Long branchId,
            ApprovalRequestType requestType,
            ApprovalRequestStatus status) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminApprovalService.class,
                        "buildSpecification(StaffUser,Long,ApprovalRequestType,ApprovalRequestStatus)");
        try {
            Specification<ApprovalRequest> specification =
                    (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
            if (!isOwner(actor)) {
                Set<Long> allowedBranchIds =
                        actor.getBranches().stream()
                                .map(branch -> branch.getId())
                                .collect(Collectors.toSet());
                specification =
                        specification.and(
                                (root, query, criteriaBuilder) ->
                                        root.get("branch").get("id").in(allowedBranchIds));
            }
            if (branchId != null) {
                specification =
                        specification.and(
                                (root, query, criteriaBuilder) ->
                                        criteriaBuilder.equal(
                                                root.get("branch").get("id"), branchId));
            }
            if (requestType != null) {
                specification =
                        specification.and(
                                (root, query, criteriaBuilder) ->
                                        criteriaBuilder.equal(
                                                root.get("requestType"), requestType));
            }
            if (status != null) {
                specification = specification.and(statusIs(status));
            }
            return specification;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminApprovalService.class,
                    "buildSpecification(StaffUser,Long,ApprovalRequestType,ApprovalRequestStatus)");
        }
    }

    /**
     * Statuses is.
     *
     * @param status the status
     * @return the status is result
     */
    private Specification<ApprovalRequest> statusIs(ApprovalRequestStatus status) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminApprovalService.class, "statusIs(ApprovalRequestStatus)");
        try {
            return (root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), status);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminApprovalService.class,
                    "statusIs(ApprovalRequestStatus)");
        }
    }

    /**
     * Validates requested branch.
     *
     * @param actor the actor
     * @param branchId the branch id
     */
    private void validateRequestedBranch(StaffUser actor, Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminApprovalService.class, "validateRequestedBranch(StaffUser,Long)");
        try {
            if (branchId == null || isOwner(actor)) {
                return;
            }
            boolean allowed =
                    actor.getBranches().stream()
                            .anyMatch(branch -> branch.getId().equals(branchId));
            if (!allowed) {
                throw new AccessDeniedException("You do not have access to this branch.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminApprovalService.class,
                    "validateRequestedBranch(StaffUser,Long)");
        }
    }

    /**
     * Reports whether owner.
     *
     * @param staff the staff
     * @return the is owner result
     */
    private boolean isOwner(StaffUser staff) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminApprovalService.class, "isOwner(StaffUser)");
        try {
            return staff.getRole() != null && "OWNER_ADMIN".equals(staff.getRole().getName());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminApprovalService.class, "isOwner(StaffUser)");
        }
    }

    /** Requires view permission. */
    private void requireViewPermission() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminApprovalService.class, "requireViewPermission()");
        try {
            staffAuthorizationService.requirePermission(PermissionName.APPROVAL_VIEW);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminApprovalService.class,
                    "requireViewPermission()");
        }
    }

    /** Requires manage permission. */
    private void requireManagePermission() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminApprovalService.class, "requireManagePermission()");
        try {
            staffAuthorizationService.requirePermission(PermissionName.APPROVAL_MANAGE);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminApprovalService.class,
                    "requireManagePermission()");
        }
    }

    /**
     * Normalizes required comment.
     *
     * @param value the value
     * @param message the message
     * @return the normalize required comment result
     */
    private String normalizeRequiredComment(String value, String message) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminApprovalService.class, "normalizeRequiredComment(String,String)");
        try {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(message);
            }
            return value.trim();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminApprovalService.class,
                    "normalizeRequiredComment(String,String)");
        }
    }

    /**
     * Tos response.
     *
     * @param request the request
     * @return the to response result
     */
    private ApprovalRequestResponse toResponse(ApprovalRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminApprovalService.class, "toResponse(ApprovalRequest)");
        try {
            return new ApprovalRequestResponse(
                    request.getId(),
                    request.getRequestNumber(),
                    request.getRequestType(),
                    request.getStatus(),
                    request.getStaffUser().getId(),
                    request.getStaffUser().getFullName(),
                    request.getStaffUser().getUsername(),
                    request.getBranch().getId(),
                    request.getBranch().getCode(),
                    request.getBranch().getName(),
                    request.getTitle(),
                    request.getSummary(),
                    request.getWorkflowVersion(),
                    request.getSubmittedAt(),
                    request.getResolvedAt(),
                    request.getCreatedAt(),
                    request.getUpdatedAt());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminApprovalService.class,
                    "toResponse(ApprovalRequest)");
        }
    }
}
