package com.gokulsweets.restaurant.staff.leave;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequest;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequestType;
import com.gokulsweets.restaurant.staff.approval.ApprovalWorkflowService;
import com.gokulsweets.restaurant.staff.leave.dto.CreateLeaveRequest;
import com.gokulsweets.restaurant.staff.leave.dto.LeaveBranchOptionResponse;
import com.gokulsweets.restaurant.staff.leave.dto.LeaveOptionsResponse;
import com.gokulsweets.restaurant.staff.leave.dto.LeaveRequestResponse;
import com.gokulsweets.restaurant.staff.leave.dto.UpdateLeaveRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffLeaveService {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(
                    "Asia/Kolkata"
            );

    private static final EnumSet<ApprovalRequestStatus>
            BLOCKING_STATUSES =
            EnumSet.of(
                    ApprovalRequestStatus.PENDING,
                    ApprovalRequestStatus.APPROVED,
                    ApprovalRequestStatus.SENT_BACK
            );

    private final LeaveRequestRepository
            leaveRequestRepository;

    private final BranchRepository
            branchRepository;

    private final StaffAuthorizationService
            staffAuthorizationService;

    private final ApprovalWorkflowService
            approvalWorkflowService;


    @Transactional(readOnly = true)
    public LeaveOptionsResponse getOptions() {

        StaffUser staff =
                staffAuthorizationService
                        .getCurrentStaff();

        List<LeaveBranchOptionResponse> branches =
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
                                        new LeaveBranchOptionResponse(
                                                branch.getId(),
                                                branch.getCode(),
                                                branch.getName(),
                                                branch.isActive()
                                        )
                        )
                        .toList();

        return new LeaveOptionsResponse(
                branches
        );
    }


    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> getMyLeaveRequests(
            int page,
            int size
    ) {

        StaffUser staff =
                staffAuthorizationService
                        .getCurrentStaff();

        PageRequest pageable =
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
                );

        return leaveRequestRepository
                .findByStaffUserId(
                        staff.getId(),
                        pageable
                )
                .map(
                        this::toResponse
                );
    }


    @Transactional(readOnly = true)
    public LeaveRequestResponse getMyLeaveRequest(
            Long leaveRequestId
    ) {

        StaffUser staff =
                staffAuthorizationService
                        .getCurrentStaff();

        return toResponse(
                getOwnRequest(
                        leaveRequestId,
                        staff.getId()
                )
        );
    }


    @Transactional
    public LeaveRequestResponse createLeaveRequest(
            CreateLeaveRequest request
    ) {

        StaffUser staff =
                staffAuthorizationService
                        .getCurrentStaff();

        validateDates(
                request.startDate(),
                request.endDate()
        );

        Branch branch =
                getAccessibleBranch(
                        staff,
                        request.branchId()
                );

        validateNoOverlap(
                staff.getId(),
                request.startDate(),
                request.endDate(),
                null
        );

        String reason =
                normalizeReason(
                        request.reason()
                );

        ApprovalRequest approval =
                approvalWorkflowService
                        .createRequest(
                                ApprovalRequestType.LEAVE,
                                staff,
                                branch,
                                createTitle(
                                        request.startDate(),
                                        request.endDate()
                                ),
                                createSummary(
                                        request.startDate(),
                                        request.endDate(),
                                        reason
                                )
                        );

        LeaveRequest leaveRequest =
                new LeaveRequest();

        leaveRequest.setStaffUser(
                staff
        );

        leaveRequest.setBranch(
                branch
        );

        leaveRequest.setApprovalRequest(
                approval
        );

        leaveRequest.setStartDate(
                request.startDate()
        );

        leaveRequest.setEndDate(
                request.endDate()
        );

        leaveRequest.setReason(
                reason
        );

        LeaveRequest saved =
                leaveRequestRepository
                        .save(
                                leaveRequest
                        );

        log.info(
                "Leave request created: leaveRequestId={}, approvalRequestId={}, staffUserId={}, branchId={}, startDate={}, endDate={}",
                saved.getId(),
                approval.getId(),
                staff.getId(),
                branch.getId(),
                saved.getStartDate(),
                saved.getEndDate()
        );

        return toResponse(
                saved
        );
    }


    @Transactional
    public LeaveRequestResponse updateSentBackRequest(
            Long leaveRequestId,
            UpdateLeaveRequest request
    ) {

        StaffUser staff =
                staffAuthorizationService
                        .getCurrentStaff();

        LeaveRequest leaveRequest =
                getOwnRequest(
                        leaveRequestId,
                        staff.getId()
                );

        if (
                leaveRequest.getApprovalRequest()
                        .getStatus()
                        != ApprovalRequestStatus.SENT_BACK
        ) {

            throw new IllegalStateException(
                    "Only a sent-back leave request can be corrected."
            );
        }

        validateDates(
                request.startDate(),
                request.endDate()
        );

        Branch branch =
                getAccessibleBranch(
                        staff,
                        request.branchId()
                );

        validateNoOverlap(
                staff.getId(),
                request.startDate(),
                request.endDate(),
                leaveRequestId
        );

        String reason =
                normalizeReason(
                        request.reason()
                );

        leaveRequest.setBranch(
                branch
        );

        leaveRequest.setStartDate(
                request.startDate()
        );

        leaveRequest.setEndDate(
                request.endDate()
        );

        leaveRequest.setReason(
                reason
        );

        ApprovalRequest approval =
                leaveRequest
                        .getApprovalRequest();

        approval.setBranch(
                branch
        );

        approval.setTitle(
                createTitle(
                        request.startDate(),
                        request.endDate()
                )
        );

        approval.setSummary(
                createSummary(
                        request.startDate(),
                        request.endDate(),
                        reason
                )
        );

        LeaveRequest saved =
                leaveRequestRepository
                        .save(
                                leaveRequest
                        );

        log.info(
                "Sent-back leave request corrected: leaveRequestId={}, approvalRequestId={}, staffUserId={}",
                saved.getId(),
                approval.getId(),
                staff.getId()
        );

        return toResponse(
                saved
        );
    }


    @Transactional
    public LeaveRequestResponse resubmit(
            Long leaveRequestId,
            String comment
    ) {

        StaffUser staff =
                staffAuthorizationService
                        .getCurrentStaff();

        LeaveRequest leaveRequest =
                getOwnRequest(
                        leaveRequestId,
                        staff.getId()
                );

        approvalWorkflowService
                .resubmit(
                        leaveRequest
                                .getApprovalRequest()
                                .getId(),
                        staff,
                        comment
                );

        return toResponse(
                leaveRequest
        );
    }


    @Transactional
    public LeaveRequestResponse cancel(
            Long leaveRequestId,
            String comment
    ) {

        StaffUser staff =
                staffAuthorizationService
                        .getCurrentStaff();

        LeaveRequest leaveRequest =
                getOwnRequest(
                        leaveRequestId,
                        staff.getId()
                );

        approvalWorkflowService
                .cancelOwnRequest(
                        leaveRequest
                                .getApprovalRequest()
                                .getId(),
                        staff,
                        comment
                );

        return toResponse(
                leaveRequest
        );
    }


    private LeaveRequest getOwnRequest(
            Long leaveRequestId,
            Long staffUserId
    ) {

        return leaveRequestRepository
                .findOwnDetailedById(
                        leaveRequestId,
                        staffUserId
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Leave request does not exist."
                                )
                );
    }


    private Branch getAccessibleBranch(
            StaffUser staff,
            Long branchId
    ) {

        if (
                branchId == null
        ) {

            throw new IllegalArgumentException(
                    "Branch is required."
            );
        }

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

        boolean accessible =
                staff.getBranches()
                        .stream()
                        .anyMatch(
                                allowedBranch ->
                                        allowedBranch.getId()
                                                .equals(
                                                        branchId
                                                )
                        );

        if (!accessible) {

            throw new AccessDeniedException(
                    "You cannot submit leave for a branch outside your access."
            );
        }

        return branch;
    }


    private void validateDates(
            LocalDate startDate,
            LocalDate endDate
    ) {

        if (
                startDate == null
                        ||
                        endDate == null
        ) {

            throw new IllegalArgumentException(
                    "Leave start date and end date are required."
            );
        }

        if (
                endDate.isBefore(
                        startDate
                )
        ) {

            throw new IllegalArgumentException(
                    "Leave end date cannot be before start date."
            );
        }

        LocalDate today =
                LocalDate.now(
                        BUSINESS_ZONE
                );

        if (
                startDate.isBefore(
                        today
                )
        ) {

            throw new IllegalArgumentException(
                    "Leave start date cannot be in the past."
            );
        }
    }


    private void validateNoOverlap(
            Long staffUserId,
            LocalDate startDate,
            LocalDate endDate,
            Long excludeLeaveRequestId
    ) {

        boolean exists =
                leaveRequestRepository
                        .existsBlockingOverlap(
                                staffUserId,
                                startDate,
                                endDate,
                                BLOCKING_STATUSES,
                                excludeLeaveRequestId
                        );

        if (exists) {

            throw new IllegalStateException(
                    "A pending, approved, or sent-back leave request already overlaps this date range."
            );
        }
    }


    private String normalizeReason(
            String value
    ) {

        if (
                value == null
                        ||
                        value.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Leave reason is required."
            );
        }

        return value.trim();
    }


    private String createTitle(
            LocalDate startDate,
            LocalDate endDate
    ) {

        if (
                startDate.equals(
                        endDate
                )
        ) {

            return "Leave request for "
                    + startDate;
        }

        return "Leave request "
                + startDate
                + " to "
                + endDate;
    }


    private String createSummary(
            LocalDate startDate,
            LocalDate endDate,
            String reason
    ) {

        return "Leave dates: "
                + startDate
                + " to "
                + endDate
                + System.lineSeparator()
                + "Reason: "
                + reason;
    }


    private LeaveRequestResponse toResponse(
            LeaveRequest leaveRequest
    ) {

        ApprovalRequest approval =
                leaveRequest
                        .getApprovalRequest();

        long totalDays =
                ChronoUnit.DAYS.between(
                        leaveRequest.getStartDate(),
                        leaveRequest.getEndDate()
                )
                        + 1;

        return new LeaveRequestResponse(
                leaveRequest.getId(),
                approval.getRequestNumber(),
                approval.getStatus(),
                approval.getWorkflowVersion(),
                leaveRequest.getBranch()
                        .getId(),
                leaveRequest.getBranch()
                        .getCode(),
                leaveRequest.getBranch()
                        .getName(),
                leaveRequest.getStartDate(),
                leaveRequest.getEndDate(),
                totalDays,
                leaveRequest.getReason(),
                approval.getSubmittedAt(),
                approval.getResolvedAt(),
                leaveRequest.getCreatedAt(),
                leaveRequest.getUpdatedAt()
        );
    }
}
