package com.gokulsweets.restaurant.staff.attendance;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.staff.attendance.dto.AttendanceActionRequest;
import com.gokulsweets.restaurant.staff.attendance.dto.AttendanceApprovalHistoryResponse;
import com.gokulsweets.restaurant.staff.attendance.dto.AttendanceOptionsResponse;
import com.gokulsweets.restaurant.staff.attendance.dto.AttendanceResponse;
import com.gokulsweets.restaurant.staff.attendance.dto.CreateAttendanceRequest;
import com.gokulsweets.restaurant.staff.attendance.dto.UpdateAttendanceRequest;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP endpoints for staff attendance operations. */
@RestController
@RequestMapping("/api/admin/me/attendance")
@RequiredArgsConstructor
public class StaffAttendanceController {

    private final StaffAttendanceService staffAttendanceService;

    /**
     * Returns options.
     *
     * @return the get options result
     */
    @GetMapping("/options")
    public ResponseEntity<AttendanceOptionsResponse> getOptions() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAttendanceController.class, "getOptions()");
        try {
            return ResponseEntity.ok(staffAttendanceService.getOptions());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffAttendanceController.class, "getOptions()");
        }
    }

    /**
     * Returns my attendance.
     *
     * @param page the page
     * @param size the size
     * @return the get my attendance result
     */
    @GetMapping
    public ResponseEntity<Page<AttendanceResponse>> getMyAttendance(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAttendanceController.class, "getMyAttendance(int,int)");
        try {
            return ResponseEntity.ok(staffAttendanceService.getMyAttendance(page, size));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceController.class,
                    "getMyAttendance(int,int)");
        }
    }

    /**
     * Returns my attendance.
     *
     * @param attendanceId the attendance id
     * @return the get my attendance result
     */
    @GetMapping("/{attendanceId}")
    public ResponseEntity<AttendanceResponse> getMyAttendance(@PathVariable Long attendanceId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAttendanceController.class, "getMyAttendance(Long)");
        try {
            return ResponseEntity.ok(staffAttendanceService.getMyAttendance(attendanceId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceController.class,
                    "getMyAttendance(Long)");
        }
    }

    /**
     * Returns my attendance history.
     *
     * @param attendanceId the attendance id
     * @return the get my attendance history result
     */
    @GetMapping("/{attendanceId}/history")
    public ResponseEntity<List<AttendanceApprovalHistoryResponse>> getMyAttendanceHistory(
            @PathVariable Long attendanceId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAttendanceController.class, "getMyAttendanceHistory(Long)");
        try {
            return ResponseEntity.ok(staffAttendanceService.getMyHistory(attendanceId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceController.class,
                    "getMyAttendanceHistory(Long)");
        }
    }

    /**
     * Creates attendance.
     *
     * @param request the request
     * @return the create attendance result
     */
    @PostMapping
    public ResponseEntity<AttendanceResponse> createAttendance(
            @Valid @RequestBody CreateAttendanceRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffAttendanceController.class,
                        "createAttendance(CreateAttendanceRequest)");
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(staffAttendanceService.createAttendance(request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceController.class,
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
    @PutMapping("/{attendanceId}")
    public ResponseEntity<AttendanceResponse> updateSentBackAttendance(
            @PathVariable Long attendanceId, @Valid @RequestBody UpdateAttendanceRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffAttendanceController.class,
                        "updateSentBackAttendance(Long,UpdateAttendanceRequest)");
        try {
            return ResponseEntity.ok(
                    staffAttendanceService.updateSentBackAttendance(attendanceId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceController.class,
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
    @PostMapping("/{attendanceId}/resubmit")
    public ResponseEntity<AttendanceResponse> resubmit(
            @PathVariable Long attendanceId, @Valid @RequestBody AttendanceActionRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffAttendanceController.class, "resubmit(Long,AttendanceActionRequest)");
        try {
            return ResponseEntity.ok(staffAttendanceService.resubmit(attendanceId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceController.class,
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
    @PostMapping("/{attendanceId}/cancel")
    public ResponseEntity<AttendanceResponse> cancel(
            @PathVariable Long attendanceId, @Valid @RequestBody AttendanceActionRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffAttendanceController.class, "cancel(Long,AttendanceActionRequest)");
        try {
            return ResponseEntity.ok(staffAttendanceService.cancel(attendanceId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAttendanceController.class,
                    "cancel(Long,AttendanceActionRequest)");
        }
    }
}
