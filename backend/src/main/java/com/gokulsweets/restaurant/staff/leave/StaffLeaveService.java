package com.gokulsweets.restaurant.staff.leave;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.observability.MethodTiming;
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

/** Coordinates staff leave operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaffLeaveService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private static final EnumSet<ApprovalRequestStatus> BLOCKING_STATUSES =
            EnumSet.of(
                    ApprovalRequestStatus.PENDING,
                    ApprovalRequestStatus.APPROVED,
                    ApprovalRequestStatus.SENT_BACK);

    private final LeaveRequestRepository leaveRequestRepository;

    private final BranchRepository branchRepository;

    private final StaffAuthorizationService staffAuthorizationService;

    private final ApprovalWorkflowService approvalWorkflowService;

    /**
     * Returns options.
     *
     * @return the get options result
     */
    @Transactional(readOnly = true)
    public LeaveOptionsResponse getOptions() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffLeaveService.class, "getOptions()");
        try {
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            List<LeaveBranchOptionResponse> branches =
                    staff.getBranches().stream()
                            .sorted(
                                    java.util.Comparator.comparing(
                                                    Branch::getName, String.CASE_INSENSITIVE_ORDER)
                                            .thenComparing(Branch::getId))
                            .map(
                                    branch ->
                                            new LeaveBranchOptionResponse(
                                                    branch.getId(),
                                                    branch.getCode(),
                                                    branch.getName(),
                                                    branch.isActive()))
                            .toList();
            return new LeaveOptionsResponse(branches);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, StaffLeaveService.class, "getOptions()");
        }
    }

    /**
     * Returns my leave requests.
     *
     * @param page the page
     * @param size the size
     * @return the get my leave requests result
     */
    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> getMyLeaveRequests(int page, int size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffLeaveService.class, "getMyLeaveRequests(int,int)");
        try {
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            PageRequest pageable =
                    PageRequest.of(
                            Math.max(page, 0),
                            Math.min(Math.max(size, 1), 100),
                            Sort.by(Sort.Direction.DESC, "createdAt")
                                    .and(Sort.by(Sort.Direction.DESC, "id")));
            return leaveRequestRepository
                    .findByStaffUserId(staff.getId(), pageable)
                    .map(this::toResponse);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffLeaveService.class,
                    "getMyLeaveRequests(int,int)");
        }
    }

    /**
     * Returns my leave request.
     *
     * @param leaveRequestId the leave request id
     * @return the get my leave request result
     */
    @Transactional(readOnly = true)
    public LeaveRequestResponse getMyLeaveRequest(Long leaveRequestId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffLeaveService.class, "getMyLeaveRequest(Long)");
        try {
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            return toResponse(getOwnRequest(leaveRequestId, staff.getId()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffLeaveService.class, "getMyLeaveRequest(Long)");
        }
    }

    /**
     * Creates leave request.
     *
     * @param request the request
     * @return the create leave request result
     */
    @Transactional
    public LeaveRequestResponse createLeaveRequest(CreateLeaveRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffLeaveService.class, "createLeaveRequest(CreateLeaveRequest)");
        try {
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            validateDates(request.startDate(), request.endDate());
            Branch branch = getAccessibleBranch(staff, request.branchId());
            validateNoOverlap(staff.getId(), request.startDate(), request.endDate(), null);
            String reason = normalizeReason(request.reason());
            ApprovalRequest approval =
                    approvalWorkflowService.createRequest(
                            ApprovalRequestType.LEAVE,
                            staff,
                            branch,
                            createTitle(request.startDate(), request.endDate()),
                            createSummary(request.startDate(), request.endDate(), reason));
            LeaveRequest leaveRequest = new LeaveRequest();
            leaveRequest.setStaffUser(staff);
            leaveRequest.setBranch(branch);
            leaveRequest.setApprovalRequest(approval);
            leaveRequest.setStartDate(request.startDate());
            leaveRequest.setEndDate(request.endDate());
            leaveRequest.setReason(reason);
            LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
            log.info(
                    "Leave request created: leaveRequestId={}, approvalRequestId={},"
                            + " staffUserId={}, branchId={}, startDate={}, endDate={}",
                    saved.getId(),
                    approval.getId(),
                    staff.getId(),
                    branch.getId(),
                    saved.getStartDate(),
                    saved.getEndDate());
            return toResponse(saved);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffLeaveService.class,
                    "createLeaveRequest(CreateLeaveRequest)");
        }
    }

    /**
     * Updates sent back request.
     *
     * @param leaveRequestId the leave request id
     * @param request the request
     * @return the update sent back request result
     */
    @Transactional
    public LeaveRequestResponse updateSentBackRequest(
            Long leaveRequestId, UpdateLeaveRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffLeaveService.class, "updateSentBackRequest(Long,UpdateLeaveRequest)");
        try {
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            LeaveRequest leaveRequest = getOwnRequest(leaveRequestId, staff.getId());
            if (leaveRequest.getApprovalRequest().getStatus() != ApprovalRequestStatus.SENT_BACK) {
                throw new IllegalStateException("Only a sent-back leave request can be corrected.");
            }
            validateDates(request.startDate(), request.endDate());
            Branch branch = getAccessibleBranch(staff, request.branchId());
            validateNoOverlap(
                    staff.getId(), request.startDate(), request.endDate(), leaveRequestId);
            String reason = normalizeReason(request.reason());
            leaveRequest.setBranch(branch);
            leaveRequest.setStartDate(request.startDate());
            leaveRequest.setEndDate(request.endDate());
            leaveRequest.setReason(reason);
            ApprovalRequest approval = leaveRequest.getApprovalRequest();
            approval.setBranch(branch);
            approval.setTitle(createTitle(request.startDate(), request.endDate()));
            approval.setSummary(createSummary(request.startDate(), request.endDate(), reason));
            LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
            log.info(
                    "Sent-back leave request corrected: leaveRequestId={}, approvalRequestId={},"
                            + " staffUserId={}",
                    saved.getId(),
                    approval.getId(),
                    staff.getId());
            return toResponse(saved);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffLeaveService.class,
                    "updateSentBackRequest(Long,UpdateLeaveRequest)");
        }
    }

    /**
     * Resubmits the operation.
     *
     * @param leaveRequestId the leave request id
     * @param comment the comment
     * @return the resubmit result
     */
    @Transactional
    public LeaveRequestResponse resubmit(Long leaveRequestId, String comment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffLeaveService.class, "resubmit(Long,String)");
        try {
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            LeaveRequest leaveRequest = getOwnRequest(leaveRequestId, staff.getId());
            approvalWorkflowService.resubmit(
                    leaveRequest.getApprovalRequest().getId(), staff, comment);
            return toResponse(leaveRequest);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffLeaveService.class, "resubmit(Long,String)");
        }
    }

    /**
     * Cancels the operation.
     *
     * @param leaveRequestId the leave request id
     * @param comment the comment
     * @return the cancel result
     */
    @Transactional
    public LeaveRequestResponse cancel(Long leaveRequestId, String comment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffLeaveService.class, "cancel(Long,String)");
        try {
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            LeaveRequest leaveRequest = getOwnRequest(leaveRequestId, staff.getId());
            approvalWorkflowService.cancelOwnRequest(
                    leaveRequest.getApprovalRequest().getId(), staff, comment);
            return toResponse(leaveRequest);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffLeaveService.class, "cancel(Long,String)");
        }
    }

    /**
     * Returns own request.
     *
     * @param leaveRequestId the leave request id
     * @param staffUserId the staff user id
     * @return the get own request result
     */
    private LeaveRequest getOwnRequest(Long leaveRequestId, Long staffUserId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffLeaveService.class, "getOwnRequest(Long,Long)");
        try {
            return leaveRequestRepository
                    .findOwnDetailedById(leaveRequestId, staffUserId)
                    .orElseThrow(
                            () -> new IllegalArgumentException("Leave request does not exist."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffLeaveService.class, "getOwnRequest(Long,Long)");
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
                MethodTiming.start(StaffLeaveService.class, "getAccessibleBranch(StaffUser,Long)");
        try {
            if (branchId == null) {
                throw new IllegalArgumentException("Branch is required.");
            }
            Branch branch =
                    branchRepository
                            .findById(branchId)
                            .orElseThrow(
                                    () -> new IllegalArgumentException("Branch does not exist."));
            boolean accessible =
                    staff.getBranches().stream()
                            .anyMatch(allowedBranch -> allowedBranch.getId().equals(branchId));
            if (!accessible) {
                throw new AccessDeniedException(
                        "You cannot submit leave for a branch outside your access.");
            }
            return branch;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffLeaveService.class,
                    "getAccessibleBranch(StaffUser,Long)");
        }
    }

    /**
     * Validates dates.
     *
     * @param startDate the start date
     * @param endDate the end date
     */
    private void validateDates(LocalDate startDate, LocalDate endDate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffLeaveService.class, "validateDates(LocalDate,LocalDate)");
        try {
            if (startDate == null || endDate == null) {
                throw new IllegalArgumentException("Leave start date and end date are required.");
            }
            if (endDate.isBefore(startDate)) {
                throw new IllegalArgumentException("Leave end date cannot be before start date.");
            }
            LocalDate today = LocalDate.now(BUSINESS_ZONE);
            if (startDate.isBefore(today)) {
                throw new IllegalArgumentException("Leave start date cannot be in the past.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffLeaveService.class,
                    "validateDates(LocalDate,LocalDate)");
        }
    }

    /**
     * Validates no overlap.
     *
     * @param staffUserId the staff user id
     * @param startDate the start date
     * @param endDate the end date
     * @param excludeLeaveRequestId the exclude leave request id
     */
    private void validateNoOverlap(
            Long staffUserId, LocalDate startDate, LocalDate endDate, Long excludeLeaveRequestId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffLeaveService.class,
                        "validateNoOverlap(Long,LocalDate,LocalDate,Long)");
        try {
            boolean exists =
                    leaveRequestRepository.existsBlockingOverlap(
                            staffUserId,
                            startDate,
                            endDate,
                            BLOCKING_STATUSES,
                            excludeLeaveRequestId);
            if (exists) {
                throw new IllegalStateException(
                        "A pending, approved, or sent-back leave request already overlaps this date"
                                + " range.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffLeaveService.class,
                    "validateNoOverlap(Long,LocalDate,LocalDate,Long)");
        }
    }

    /**
     * Normalizes reason.
     *
     * @param value the value
     * @return the normalize reason result
     */
    private String normalizeReason(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffLeaveService.class, "normalizeReason(String)");
        try {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Leave reason is required.");
            }
            return value.trim();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffLeaveService.class, "normalizeReason(String)");
        }
    }

    /**
     * Creates title.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @return the create title result
     */
    private String createTitle(LocalDate startDate, LocalDate endDate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffLeaveService.class, "createTitle(LocalDate,LocalDate)");
        try {
            if (startDate.equals(endDate)) {
                return "Leave request for " + startDate;
            }
            return "Leave request " + startDate + " to " + endDate;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffLeaveService.class,
                    "createTitle(LocalDate,LocalDate)");
        }
    }

    /**
     * Creates summary.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param reason the reason
     * @return the create summary result
     */
    private String createSummary(LocalDate startDate, LocalDate endDate, String reason) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffLeaveService.class, "createSummary(LocalDate,LocalDate,String)");
        try {
            return "Leave dates: "
                    + startDate
                    + " to "
                    + endDate
                    + System.lineSeparator()
                    + "Reason: "
                    + reason;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffLeaveService.class,
                    "createSummary(LocalDate,LocalDate,String)");
        }
    }

    /**
     * Tos response.
     *
     * @param leaveRequest the leave request
     * @return the to response result
     */
    private LeaveRequestResponse toResponse(LeaveRequest leaveRequest) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffLeaveService.class, "toResponse(LeaveRequest)");
        try {
            ApprovalRequest approval = leaveRequest.getApprovalRequest();
            long totalDays =
                    ChronoUnit.DAYS.between(leaveRequest.getStartDate(), leaveRequest.getEndDate())
                            + 1;
            return new LeaveRequestResponse(
                    leaveRequest.getId(),
                    approval.getRequestNumber(),
                    approval.getStatus(),
                    approval.getWorkflowVersion(),
                    leaveRequest.getBranch().getId(),
                    leaveRequest.getBranch().getCode(),
                    leaveRequest.getBranch().getName(),
                    leaveRequest.getStartDate(),
                    leaveRequest.getEndDate(),
                    totalDays,
                    leaveRequest.getReason(),
                    approval.getSubmittedAt(),
                    approval.getResolvedAt(),
                    leaveRequest.getCreatedAt(),
                    leaveRequest.getUpdatedAt());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffLeaveService.class, "toResponse(LeaveRequest)");
        }
    }
}
