package com.gokulsweets.restaurant.staff.attendance;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequest;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequestHistoryRepository;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequestType;
import com.gokulsweets.restaurant.staff.approval.ApprovalWorkflowService;
import com.gokulsweets.restaurant.staff.attendance.dto.AttendanceActionRequest;
import com.gokulsweets.restaurant.staff.attendance.dto.AttendanceApprovalHistoryResponse;
import com.gokulsweets.restaurant.staff.attendance.dto.AttendanceBranchOptionResponse;
import com.gokulsweets.restaurant.staff.attendance.dto.AttendanceOptionsResponse;
import com.gokulsweets.restaurant.staff.attendance.dto.AttendanceResponse;
import com.gokulsweets.restaurant.staff.attendance.dto.CreateAttendanceRequest;
import com.gokulsweets.restaurant.staff.attendance.dto.UpdateAttendanceRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;

/** Coordinates staff attendance operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaffAttendanceService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private static final EnumSet<ApprovalRequestStatus> BLOCKING_STATUSES =
            EnumSet.of(
                    ApprovalRequestStatus.PENDING,
                    ApprovalRequestStatus.APPROVED,
                    ApprovalRequestStatus.SENT_BACK);

    private final StaffAttendanceRepository attendanceRepository;

    private final BranchRepository branchRepository;

    private final ApprovalRequestHistoryRepository approvalRequestHistoryRepository;

    private final StaffAuthorizationService staffAuthorizationService;

    private final ApprovalWorkflowService approvalWorkflowService;

    /**
     * Returns options.
     *
     * @return the get options result
     */
    @Transactional(readOnly = true)
    public AttendanceOptionsResponse getOptions() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAttendanceService.class, "getOptions()");
        try {
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            List<AttendanceBranchOptionResponse> branches =
                    staff.getBranches().stream()
                            .sorted(
                                    java.util.Comparator.comparing(
                                                    Branch::getName, String.CASE_INSENSITIVE_ORDER)
                                            .thenComparing(Branch::getId))
                            .map(
                                    branch ->
                                            new AttendanceBranchOptionResponse(
                                                    branch.getId(),
                                                    branch.getCode(),
                                                    branch.getName(),
                                                    branch.isActive()))
                            .toList();
            return new AttendanceOptionsResponse(branches, List.of(AttendanceType.values()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffAttendanceService.class, "getOptions()");
        }
    }

    /**
     * Returns my attendance.
     *
     * @param page the page
     * @param size the size
     * @return the get my attendance result
     */
    @Transactional(readOnly = true)
    public Page<AttendanceResponse> getMyAttendance(int page, int size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAttendanceService.class, "getMyAttendance(int,int)");
        try {
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            PageRequest pageable =
                    PageRequest.of(
                            Math.max(page, 0),
                            Math.min(Math.max(size, 1), 100),
                            Sort.by(Sort.Direction.DESC, "attendanceDate")
                                    .and(Sort.by(Sort.Direction.DESC, "id")));
            return attendanceRepository
                    .findByStaffUserId(staff.getId(), pageable)
                    .map(this::toResponse);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceService.class,
                    "getMyAttendance(int,int)");
        }
    }

    /**
     * Returns my attendance.
     *
     * @param attendanceId the attendance id
     * @return the get my attendance result
     */
    @Transactional(readOnly = true)
    public AttendanceResponse getMyAttendance(Long attendanceId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAttendanceService.class, "getMyAttendance(Long)");
        try {
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            return toResponse(getOwnAttendance(attendanceId, staff.getId()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceService.class,
                    "getMyAttendance(Long)");
        }
    }

    /**
     * Returns my history.
     *
     * @param attendanceId the attendance id
     * @return the get my history result
     */
    @Transactional(readOnly = true)
    public List<AttendanceApprovalHistoryResponse> getMyHistory(Long attendanceId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAttendanceService.class, "getMyHistory(Long)");
        try {
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            StaffAttendance attendance = getOwnAttendance(attendanceId, staff.getId());
            Long approvalRequestId = attendance.getApprovalRequest().getId();
            return approvalRequestHistoryRepository
                    .findByApprovalRequestIdOrderByCreatedAtAscIdAsc(approvalRequestId)
                    .stream()
                    .map(
                            history ->
                                    new AttendanceApprovalHistoryResponse(
                                            history.getId(),
                                            history.getAction(),
                                            history.getFromStatus(),
                                            history.getToStatus(),
                                            history.getActorName(),
                                            history.getComment(),
                                            history.getCreatedAt()))
                    .toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffAttendanceService.class, "getMyHistory(Long)");
        }
    }

    /**
     * Creates attendance.
     *
     * @param request the request
     * @return the create attendance result
     */
    @Transactional
    public AttendanceResponse createAttendance(CreateAttendanceRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffAttendanceService.class, "createAttendance(CreateAttendanceRequest)");
        try {
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            validateDate(request.attendanceDate());
            validateTimes(request.attendanceType(), request.checkInTime(), request.checkOutTime());
            Branch branch = getAccessibleBranch(staff, request.branchId());
            validateNoBlockingAttendance(
                    staff.getId(), branch.getId(), request.attendanceDate(), null);
            String note = normalizeNullable(request.note());
            ApprovalRequest approval =
                    approvalWorkflowService.createRequest(
                            ApprovalRequestType.ATTENDANCE,
                            staff,
                            branch,
                            createTitle(request.attendanceDate(), request.attendanceType()),
                            createSummary(
                                    request.attendanceDate(),
                                    request.attendanceType(),
                                    request.checkInTime(),
                                    request.checkOutTime(),
                                    note));
            StaffAttendance attendance = new StaffAttendance();
            attendance.setStaffUser(staff);
            attendance.setBranch(branch);
            attendance.setApprovalRequest(approval);
            attendance.setAttendanceDate(request.attendanceDate());
            attendance.setAttendanceType(request.attendanceType());
            attendance.setCheckInTime(
                    normalizedCheckIn(request.attendanceType(), request.checkInTime()));
            attendance.setCheckOutTime(
                    normalizedCheckOut(request.attendanceType(), request.checkOutTime()));
            attendance.setNote(note);
            StaffAttendance saved = attendanceRepository.save(attendance);
            if (shouldAutoApprove(request)) {
                ApprovalRequest autoApproved =
                        approvalWorkflowService.autoApprove(
                                approval.getId(),
                                staff,
                                "Automatically approved: same-day PRESENT self-attendance.");
                saved.setApprovalRequest(autoApproved);
                log.info(
                        "Attendance automatically approved: attendanceId={}, approvalRequestId={},"
                                + " staffUserId={}, branchId={}, attendanceDate={}",
                        saved.getId(),
                        autoApproved.getId(),
                        staff.getId(),
                        branch.getId(),
                        saved.getAttendanceDate());
            } else {
                log.info(
                        "Attendance submitted for approval: attendanceId={}, approvalRequestId={},"
                            + " staffUserId={}, branchId={}, attendanceDate={}, attendanceType={}",
                        saved.getId(),
                        approval.getId(),
                        staff.getId(),
                        branch.getId(),
                        saved.getAttendanceDate(),
                        saved.getAttendanceType());
            }
            return toResponse(saved);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceService.class,
                    "createAttendance(CreateAttendanceRequest)");
        }
    }

    /**
     * Updates sent back attendance.
     *
     * @param attendanceId the attendance id
     * @param request the request
     * @return the update sent back attendance result
     */
    @Transactional
    public AttendanceResponse updateSentBackAttendance(
            Long attendanceId, UpdateAttendanceRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffAttendanceService.class,
                        "updateSentBackAttendance(Long,UpdateAttendanceRequest)");
        try {
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            StaffAttendance attendance = getOwnAttendance(attendanceId, staff.getId());
            if (attendance.getApprovalRequest().getStatus() != ApprovalRequestStatus.SENT_BACK) {
                throw new IllegalStateException("Only sent-back attendance can be corrected.");
            }
            validateDate(request.attendanceDate());
            validateTimes(request.attendanceType(), request.checkInTime(), request.checkOutTime());
            Branch branch = getAccessibleBranch(staff, request.branchId());
            validateNoBlockingAttendance(
                    staff.getId(), branch.getId(), request.attendanceDate(), attendanceId);
            String note = normalizeNullable(request.note());
            attendance.setBranch(branch);
            attendance.setAttendanceDate(request.attendanceDate());
            attendance.setAttendanceType(request.attendanceType());
            attendance.setCheckInTime(
                    normalizedCheckIn(request.attendanceType(), request.checkInTime()));
            attendance.setCheckOutTime(
                    normalizedCheckOut(request.attendanceType(), request.checkOutTime()));
            attendance.setNote(note);
            ApprovalRequest approval = attendance.getApprovalRequest();
            approval.setBranch(branch);
            approval.setTitle(createTitle(request.attendanceDate(), request.attendanceType()));
            approval.setSummary(
                    createSummary(
                            request.attendanceDate(),
                            request.attendanceType(),
                            request.checkInTime(),
                            request.checkOutTime(),
                            note));
            StaffAttendance saved = attendanceRepository.save(attendance);
            log.info(
                    "Sent-back attendance corrected: attendanceId={}, approvalRequestId={},"
                            + " staffUserId={}",
                    saved.getId(),
                    approval.getId(),
                    staff.getId());
            return toResponse(saved);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceService.class,
                    "updateSentBackAttendance(Long,UpdateAttendanceRequest)");
        }
    }

    /**
     * Resubmits the operation.
     *
     * @param attendanceId the attendance id
     * @param request the request
     * @return the resubmit result
     */
    @Transactional
    public AttendanceResponse resubmit(Long attendanceId, AttendanceActionRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffAttendanceService.class, "resubmit(Long,AttendanceActionRequest)");
        try {
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            StaffAttendance attendance = getOwnAttendance(attendanceId, staff.getId());
            approvalWorkflowService.resubmit(
                    attendance.getApprovalRequest().getId(), staff, request.comment());
            return toResponse(attendance);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceService.class,
                    "resubmit(Long,AttendanceActionRequest)");
        }
    }

    /**
     * Cancels the operation.
     *
     * @param attendanceId the attendance id
     * @param request the request
     * @return the cancel result
     */
    @Transactional
    public AttendanceResponse cancel(Long attendanceId, AttendanceActionRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffAttendanceService.class, "cancel(Long,AttendanceActionRequest)");
        try {
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            StaffAttendance attendance = getOwnAttendance(attendanceId, staff.getId());
            approvalWorkflowService.cancelOwnRequest(
                    attendance.getApprovalRequest().getId(), staff, request.comment());
            return toResponse(attendance);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceService.class,
                    "cancel(Long,AttendanceActionRequest)");
        }
    }

    /**
     * Returns own attendance.
     *
     * @param attendanceId the attendance id
     * @param staffUserId the staff user id
     * @return the get own attendance result
     */
    private StaffAttendance getOwnAttendance(Long attendanceId, Long staffUserId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAttendanceService.class, "getOwnAttendance(Long,Long)");
        try {
            return attendanceRepository
                    .findOwnDetailedById(attendanceId, staffUserId)
                    .orElseThrow(
                            () ->
                                    new IllegalArgumentException(
                                            "Attendance record does not exist."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceService.class,
                    "getOwnAttendance(Long,Long)");
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
                        StaffAttendanceService.class, "getAccessibleBranch(StaffUser,Long)");
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
                        "You cannot submit attendance for a branch outside your access.");
            }
            return branch;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceService.class,
                    "getAccessibleBranch(StaffUser,Long)");
        }
    }

    /**
     * Reports whether auto approve.
     *
     * @param request the request
     * @return the should auto approve result
     */
    private boolean shouldAutoApprove(CreateAttendanceRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffAttendanceService.class, "shouldAutoApprove(CreateAttendanceRequest)");
        try {
            LocalDate today = LocalDate.now(BUSINESS_ZONE);
            return request.attendanceType() == AttendanceType.PRESENT
                    && today.equals(request.attendanceDate());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceService.class,
                    "shouldAutoApprove(CreateAttendanceRequest)");
        }
    }

    /**
     * Validates date.
     *
     * @param attendanceDate the attendance date
     */
    private void validateDate(LocalDate attendanceDate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAttendanceService.class, "validateDate(LocalDate)");
        try {
            if (attendanceDate == null) {
                throw new IllegalArgumentException("Attendance date is required.");
            }
            LocalDate today = LocalDate.now(BUSINESS_ZONE);
            if (attendanceDate.isAfter(today)) {
                throw new IllegalArgumentException(
                        "Attendance cannot be submitted for a future date.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceService.class,
                    "validateDate(LocalDate)");
        }
    }

    /**
     * Validates times.
     *
     * @param attendanceType the attendance type
     * @param checkInTime the check in time
     * @param checkOutTime the check out time
     */
    private void validateTimes(
            AttendanceType attendanceType, LocalTime checkInTime, LocalTime checkOutTime) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffAttendanceService.class,
                        "validateTimes(AttendanceType,LocalTime,LocalTime)");
        try {
            if (attendanceType == null) {
                throw new IllegalArgumentException("Attendance type is required.");
            }
            if (attendanceType == AttendanceType.ABSENT
                    && (checkInTime != null || checkOutTime != null)) {
                throw new IllegalArgumentException(
                        "Absent attendance cannot contain check-in or check-out time.");
            }
            if (checkInTime != null && checkOutTime != null && !checkOutTime.isAfter(checkInTime)) {
                throw new IllegalArgumentException("Check-out time must be after check-in time.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceService.class,
                    "validateTimes(AttendanceType,LocalTime,LocalTime)");
        }
    }

    /**
     * Validates no blocking attendance.
     *
     * @param staffUserId the staff user id
     * @param branchId the branch id
     * @param attendanceDate the attendance date
     * @param excludeAttendanceId the exclude attendance id
     */
    private void validateNoBlockingAttendance(
            Long staffUserId, Long branchId, LocalDate attendanceDate, Long excludeAttendanceId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffAttendanceService.class,
                        "validateNoBlockingAttendance(Long,Long,LocalDate,Long)");
        try {
            boolean exists =
                    attendanceRepository.existsBlockingAttendance(
                            staffUserId,
                            branchId,
                            attendanceDate,
                            BLOCKING_STATUSES,
                            excludeAttendanceId);
            if (exists) {
                throw new IllegalStateException(
                        "A pending, approved, or sent-back attendance record already exists for"
                                + " this branch and date.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceService.class,
                    "validateNoBlockingAttendance(Long,Long,LocalDate,Long)");
        }
    }

    /**
     * Normalizeds check in.
     *
     * @param attendanceType the attendance type
     * @param value the value
     * @return the normalized check in result
     */
    private LocalTime normalizedCheckIn(AttendanceType attendanceType, LocalTime value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffAttendanceService.class,
                        "normalizedCheckIn(AttendanceType,LocalTime)");
        try {
            return attendanceType == AttendanceType.ABSENT ? null : value;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceService.class,
                    "normalizedCheckIn(AttendanceType,LocalTime)");
        }
    }

    /**
     * Normalizeds check out.
     *
     * @param attendanceType the attendance type
     * @param value the value
     * @return the normalized check out result
     */
    private LocalTime normalizedCheckOut(AttendanceType attendanceType, LocalTime value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffAttendanceService.class,
                        "normalizedCheckOut(AttendanceType,LocalTime)");
        try {
            return attendanceType == AttendanceType.ABSENT ? null : value;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceService.class,
                    "normalizedCheckOut(AttendanceType,LocalTime)");
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
                MethodTiming.start(StaffAttendanceService.class, "normalizeNullable(String)");
        try {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceService.class,
                    "normalizeNullable(String)");
        }
    }

    /**
     * Creates title.
     *
     * @param attendanceDate the attendance date
     * @param attendanceType the attendance type
     * @return the create title result
     */
    private String createTitle(LocalDate attendanceDate, AttendanceType attendanceType) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffAttendanceService.class, "createTitle(LocalDate,AttendanceType)");
        try {
            return "Attendance " + attendanceDate + " - " + attendanceType.name();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceService.class,
                    "createTitle(LocalDate,AttendanceType)");
        }
    }

    /**
     * Creates summary.
     *
     * @param attendanceDate the attendance date
     * @param attendanceType the attendance type
     * @param checkInTime the check in time
     * @param checkOutTime the check out time
     * @param note the note
     * @return the create summary result
     */
    private String createSummary(
            LocalDate attendanceDate,
            AttendanceType attendanceType,
            LocalTime checkInTime,
            LocalTime checkOutTime,
            String note) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffAttendanceService.class,
                        "createSummary(LocalDate,AttendanceType,LocalTime,LocalTime,String)");
        try {
            StringBuilder summary = new StringBuilder();
            summary.append("Attendance date: ");
            summary.append(attendanceDate);
            summary.append(System.lineSeparator());
            summary.append("Type: ");
            summary.append(attendanceType.name());
            if (checkInTime != null) {
                summary.append(System.lineSeparator());
                summary.append("Check-in: ");
                summary.append(checkInTime);
            }
            if (checkOutTime != null) {
                summary.append(System.lineSeparator());
                summary.append("Check-out: ");
                summary.append(checkOutTime);
            }
            if (note != null) {
                summary.append(System.lineSeparator());
                summary.append("Note: ");
                summary.append(note);
            }
            return summary.toString();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceService.class,
                    "createSummary(LocalDate,AttendanceType,LocalTime,LocalTime,String)");
        }
    }

    /**
     * Tos response.
     *
     * @param attendance the attendance
     * @return the to response result
     */
    private AttendanceResponse toResponse(StaffAttendance attendance) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAttendanceService.class, "toResponse(StaffAttendance)");
        try {
            ApprovalRequest approval = attendance.getApprovalRequest();
            return new AttendanceResponse(
                    attendance.getId(),
                    approval.getRequestNumber(),
                    approval.getStatus(),
                    approval.getWorkflowVersion(),
                    attendance.getBranch().getId(),
                    attendance.getBranch().getCode(),
                    attendance.getBranch().getName(),
                    attendance.getAttendanceDate(),
                    attendance.getAttendanceType(),
                    attendance.getCheckInTime(),
                    attendance.getCheckOutTime(),
                    attendance.getNote(),
                    approval.getSubmittedAt(),
                    approval.getResolvedAt(),
                    attendance.getCreatedAt(),
                    attendance.getUpdatedAt());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceService.class,
                    "toResponse(StaffAttendance)");
        }
    }
}
