package com.gokulsweets.restaurant.staff.approval;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.staff.approval.dto.ApprovalActionRequest;
import com.gokulsweets.restaurant.staff.approval.dto.ApprovalCountsResponse;
import com.gokulsweets.restaurant.staff.approval.dto.ApprovalHistoryResponse;
import com.gokulsweets.restaurant.staff.approval.dto.ApprovalRequestDetailResponse;
import com.gokulsweets.restaurant.staff.approval.dto.ApprovalRequestResponse;
import com.gokulsweets.restaurant.staff.approval.dto.ApprovalOptionsResponse;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminApprovalService {

    private static final int MAX_PAGE_SIZE =
            100;

    private final ApprovalRequestRepository
            approvalRequestRepository;

    private final ApprovalRequestHistoryRepository
            historyRepository;

    private final ApprovalWorkflowService
            approvalWorkflowService;

    private final BranchRepository
            branchRepository;

    private final StaffAuthorizationService
            staffAuthorizationService;


    @Transactional(readOnly = true)
    public Page<ApprovalRequestResponse> getApprovals(
            Long branchId,
            ApprovalRequestType requestType,
            ApprovalRequestStatus status,
            int page,
            int size
    ) {

        requireViewPermission();

        StaffUser actor =
                staffAuthorizationService
                        .getCurrentStaff();

        validateRequestedBranch(
                actor,
                branchId
        );

        Pageable pageable =
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
                                MAX_PAGE_SIZE
                        ),
                        Sort.by(
                                Sort.Direction.DESC,
                                "submittedAt"
                        ).and(
                                Sort.by(
                                        Sort.Direction.DESC,
                                        "id"
                                )
                        )
                );

        Specification<ApprovalRequest> specification =
                buildSpecification(
                        actor,
                        branchId,
                        requestType,
                        status
                );

        return approvalRequestRepository
                .findAll(
                        specification,
                        pageable
                )
                .map(
                        this::toResponse
                );
    }


    @Transactional(readOnly = true)
    public ApprovalCountsResponse getCounts(
            Long branchId,
            ApprovalRequestType requestType
    ) {

        requireViewPermission();

        StaffUser actor =
                staffAuthorizationService
                        .getCurrentStaff();

        validateRequestedBranch(
                actor,
                branchId
        );

        Specification<ApprovalRequest> base =
                buildSpecification(
                        actor,
                        branchId,
                        requestType,
                        null
                );

        long pending =
                approvalRequestRepository.count(
                        base.and(
                                statusIs(
                                        ApprovalRequestStatus.PENDING
                                )
                        )
                );

        long sentBack =
                approvalRequestRepository.count(
                        base.and(
                                statusIs(
                                        ApprovalRequestStatus.SENT_BACK
                                )
                        )
                );

        long approved =
                approvalRequestRepository.count(
                        base.and(
                                statusIs(
                                        ApprovalRequestStatus.APPROVED
                                )
                        )
                );

        long rejected =
                approvalRequestRepository.count(
                        base.and(
                                statusIs(
                                        ApprovalRequestStatus.REJECTED
                                )
                        )
                );

        long cancelled =
                approvalRequestRepository.count(
                        base.and(
                                statusIs(
                                        ApprovalRequestStatus.CANCELLED
                                )
                        )
                );

        return new ApprovalCountsResponse(
                pending,
                sentBack,
                approved,
                rejected,
                cancelled,
                pending
                        + sentBack
                        + approved
                        + rejected
                        + cancelled
        );
    }


    @Transactional(readOnly = true)
    public ApprovalOptionsResponse getOptions() {

        requireViewPermission();

        StaffUser actor =
                staffAuthorizationService
                        .getCurrentStaff();

        List<ApprovalOptionsResponse.BranchOption> branches =
                (
                        isOwner(actor)
                                ? branchRepository
                                .findAll()
                                : actor.getBranches()
                                .stream()
                                .toList()
                )
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
                                        new ApprovalOptionsResponse.BranchOption(
                                                branch.getId(),
                                                branch.getCode(),
                                                branch.getName(),
                                                branch.isActive()
                                        )
                        )
                        .toList();

        return new ApprovalOptionsResponse(
                branches,
                List.of(
                        ApprovalRequestType.values()
                ),
                List.of(
                        ApprovalRequestStatus.values()
                )
        );
    }


    @Transactional(readOnly = true)
    public ApprovalRequestDetailResponse getApproval(
            Long approvalRequestId
    ) {

        requireViewPermission();

        ApprovalRequest request =
                approvalRequestRepository
                        .findById(
                                approvalRequestId
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Approval request does not exist."
                                        )
                        );

        staffAuthorizationService
                .requireBranchAccess(
                        request.getBranch()
                                .getId()
                );

        var history =
                historyRepository
                        .findByApprovalRequestIdOrderByCreatedAtAscIdAsc(
                                approvalRequestId
                        )
                        .stream()
                        .map(
                                item ->
                                        new ApprovalHistoryResponse(
                                                item.getId(),
                                                item.getAction(),
                                                item.getFromStatus(),
                                                item.getToStatus(),
                                                item.getActorStaffUser()
                                                        .getId(),
                                                item.getActorName(),
                                                item.getComment(),
                                                item.getCreatedAt()
                                        )
                        )
                        .toList();

        return new ApprovalRequestDetailResponse(
                toResponse(
                        request
                ),
                history
        );
    }


    @Transactional
    public ApprovalRequestResponse approve(
            Long approvalRequestId,
            ApprovalActionRequest actionRequest
    ) {

        return transitionPending(
                approvalRequestId,
                ApprovalRequestStatus.APPROVED,
                ApprovalRequestAction.APPROVED,
                actionRequest.comment()
        );
    }


    @Transactional
    public ApprovalRequestResponse reject(
            Long approvalRequestId,
            ApprovalActionRequest actionRequest
    ) {

        String comment =
                normalizeRequiredComment(
                        actionRequest.comment(),
                        "A rejection reason is required."
                );

        return transitionPending(
                approvalRequestId,
                ApprovalRequestStatus.REJECTED,
                ApprovalRequestAction.REJECTED,
                comment
        );
    }


    @Transactional
    public ApprovalRequestResponse sendBack(
            Long approvalRequestId,
            ApprovalActionRequest actionRequest
    ) {

        String comment =
                normalizeRequiredComment(
                        actionRequest.comment(),
                        "A correction note is required when sending a request back."
                );

        return transitionPending(
                approvalRequestId,
                ApprovalRequestStatus.SENT_BACK,
                ApprovalRequestAction.SENT_BACK,
                comment
        );
    }


    private ApprovalRequestResponse transitionPending(
            Long approvalRequestId,
            ApprovalRequestStatus targetStatus,
            ApprovalRequestAction action,
            String comment
    ) {

        requireManagePermission();

        StaffUser actor =
                staffAuthorizationService
                        .getCurrentStaff();

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

        staffAuthorizationService
                .requireBranchAccess(
                        request.getBranch()
                                .getId()
                );

        if (
                request.getStatus()
                        != ApprovalRequestStatus.PENDING
        ) {

            throw new IllegalStateException(
                    "Only a pending request can be actioned."
            );
        }

        ApprovalRequestStatus fromStatus =
                request.getStatus();

        request.setStatus(
                targetStatus
        );

        request.setResolvedAt(
                LocalDateTime.now()
        );

        ApprovalRequest saved =
                approvalRequestRepository
                        .save(
                                request
                        );

        approvalWorkflowService
                .appendHistory(
                        saved,
                        action,
                        fromStatus,
                        targetStatus,
                        actor,
                        comment
                );

        log.info(
                "Approval request actioned: approvalRequestId={}, requestNumber={}, action={}, actedByStaffUserId={}",
                saved.getId(),
                saved.getRequestNumber(),
                action,
                actor.getId()
        );

        return toResponse(
                saved
        );
    }


    private Specification<ApprovalRequest> buildSpecification(
            StaffUser actor,
            Long branchId,
            ApprovalRequestType requestType,
            ApprovalRequestStatus status
    ) {

        Specification<ApprovalRequest> specification =
                (
                        root,
                        query,
                        criteriaBuilder
                ) ->
                        criteriaBuilder.conjunction();

        if (
                !isOwner(
                        actor
                )
        ) {

            Set<Long> allowedBranchIds =
                    actor.getBranches()
                            .stream()
                            .map(
                                    branch ->
                                            branch.getId()
                            )
                            .collect(
                                    Collectors.toSet()
                            );

            specification =
                    specification.and(
                            (root, query, criteriaBuilder) ->
                                    root.get("branch")
                                            .get("id")
                                            .in(
                                                    allowedBranchIds
                                            )
                    );
        }

        if (
                branchId != null
        ) {

            specification =
                    specification.and(
                            (root, query, criteriaBuilder) ->
                                    criteriaBuilder.equal(
                                            root.get("branch")
                                                    .get("id"),
                                            branchId
                                    )
                    );
        }

        if (
                requestType != null
        ) {

            specification =
                    specification.and(
                            (root, query, criteriaBuilder) ->
                                    criteriaBuilder.equal(
                                            root.get("requestType"),
                                            requestType
                                    )
                    );
        }

        if (
                status != null
        ) {

            specification =
                    specification.and(
                            statusIs(
                                    status
                            )
                    );
        }

        return specification;
    }


    private Specification<ApprovalRequest> statusIs(
            ApprovalRequestStatus status
    ) {

        return (
                root,
                query,
                criteriaBuilder
        ) ->
                criteriaBuilder.equal(
                        root.get("status"),
                        status
                );
    }


    private void validateRequestedBranch(
            StaffUser actor,
            Long branchId
    ) {

        if (
                branchId == null
                        ||
                        isOwner(
                                actor
                        )
        ) {

            return;
        }

        boolean allowed =
                actor.getBranches()
                        .stream()
                        .anyMatch(
                                branch ->
                                        branch.getId()
                                                .equals(
                                                        branchId
                                                )
                        );

        if (!allowed) {

            throw new AccessDeniedException(
                    "You do not have access to this branch."
            );
        }
    }


    private boolean isOwner(
            StaffUser staff
    ) {

        return staff.getRole()
                != null
                &&
                "OWNER_ADMIN".equals(
                        staff.getRole()
                                .getName()
                );
    }


    private void requireViewPermission() {

        staffAuthorizationService
                .requirePermission(
                        PermissionName.APPROVAL_VIEW
                );
    }


    private void requireManagePermission() {

        staffAuthorizationService
                .requirePermission(
                        PermissionName.APPROVAL_MANAGE
                );
    }


    private String normalizeRequiredComment(
            String value,
            String message
    ) {

        if (
                value == null
                        ||
                        value.isBlank()
        ) {

            throw new IllegalArgumentException(
                    message
            );
        }

        return value.trim();
    }


    private ApprovalRequestResponse toResponse(
            ApprovalRequest request
    ) {

        return new ApprovalRequestResponse(
                request.getId(),
                request.getRequestNumber(),
                request.getRequestType(),
                request.getStatus(),
                request.getStaffUser()
                        .getId(),
                request.getStaffUser()
                        .getFullName(),
                request.getStaffUser()
                        .getUsername(),
                request.getBranch()
                        .getId(),
                request.getBranch()
                        .getCode(),
                request.getBranch()
                        .getName(),
                request.getTitle(),
                request.getSummary(),
                request.getWorkflowVersion(),
                request.getSubmittedAt(),
                request.getResolvedAt(),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }
}
