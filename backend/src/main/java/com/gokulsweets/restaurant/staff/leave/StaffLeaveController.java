package com.gokulsweets.restaurant.staff.leave;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.staff.leave.dto.CreateLeaveRequest;
import com.gokulsweets.restaurant.staff.leave.dto.LeaveActionRequest;
import com.gokulsweets.restaurant.staff.leave.dto.LeaveOptionsResponse;
import com.gokulsweets.restaurant.staff.leave.dto.LeaveRequestResponse;
import com.gokulsweets.restaurant.staff.leave.dto.UpdateLeaveRequest;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for staff leave operations. */
@RestController
@RequestMapping("/api/admin/me/leave-requests")
@RequiredArgsConstructor
public class StaffLeaveController {

    private final StaffLeaveService staffLeaveService;

    /**
     * Returns options.
     *
     * @return the get options result
     */
    @GetMapping("/options")
    public ResponseEntity<LeaveOptionsResponse> getOptions() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffLeaveController.class, "getOptions()");
        try {
            return ResponseEntity.ok(staffLeaveService.getOptions());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffLeaveController.class, "getOptions()");
        }
    }

    /**
     * Returns my leave requests.
     *
     * @param page the page
     * @param size the size
     * @return the get my leave requests result
     */
    @GetMapping
    public ResponseEntity<Page<LeaveRequestResponse>> getMyLeaveRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffLeaveController.class, "getMyLeaveRequests(int,int)");
        try {
            return ResponseEntity.ok(staffLeaveService.getMyLeaveRequests(page, size));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffLeaveController.class,
                    "getMyLeaveRequests(int,int)");
        }
    }

    /**
     * Returns my leave request.
     *
     * @param leaveRequestId the leave request id
     * @return the get my leave request result
     */
    @GetMapping("/{leaveRequestId}")
    public ResponseEntity<LeaveRequestResponse> getMyLeaveRequest(
            @PathVariable Long leaveRequestId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffLeaveController.class, "getMyLeaveRequest(Long)");
        try {
            return ResponseEntity.ok(staffLeaveService.getMyLeaveRequest(leaveRequestId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffLeaveController.class,
                    "getMyLeaveRequest(Long)");
        }
    }

    /**
     * Creates leave request.
     *
     * @param request the request
     * @return the create leave request result
     */
    @PostMapping
    public ResponseEntity<LeaveRequestResponse> createLeaveRequest(
            @Valid @RequestBody CreateLeaveRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffLeaveController.class, "createLeaveRequest(CreateLeaveRequest)");
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(staffLeaveService.createLeaveRequest(request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffLeaveController.class,
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
    @PutMapping("/{leaveRequestId}")
    public ResponseEntity<LeaveRequestResponse> updateSentBackRequest(
            @PathVariable Long leaveRequestId, @Valid @RequestBody UpdateLeaveRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffLeaveController.class,
                        "updateSentBackRequest(Long,UpdateLeaveRequest)");
        try {
            return ResponseEntity.ok(
                    staffLeaveService.updateSentBackRequest(leaveRequestId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffLeaveController.class,
                    "updateSentBackRequest(Long,UpdateLeaveRequest)");
        }
    }

    /**
     * Handles {@code POST /api/admin/me/leave-requests/{leaveRequestId}/resubmit} for staff leave.
     *
     * <p>Delegates to {@code staffLeaveService.resubmit(...)}.
     *
     * @param leaveRequestId the leave request id supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code ResponseEntity.ok(staffLeaveService.resubmit(leaveRequestId,
     *     request.comment()))}
     */
    @PostMapping("/{leaveRequestId}/resubmit")
    public ResponseEntity<LeaveRequestResponse> resubmit(
            @PathVariable Long leaveRequestId, @Valid @RequestBody LeaveActionRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffLeaveController.class, "resubmit(Long,LeaveActionRequest)");
        try {
            return ResponseEntity.ok(staffLeaveService.resubmit(leaveRequestId, request.comment()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffLeaveController.class,
                    "resubmit(Long,LeaveActionRequest)");
        }
    }

    /**
     * Handles {@code POST /api/admin/me/leave-requests/{leaveRequestId}/cancel} for staff leave.
     *
     * <p>Delegates to {@code staffLeaveService.cancel(...)}.
     *
     * @param leaveRequestId the leave request id supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code ResponseEntity.ok(staffLeaveService.cancel(leaveRequestId,
     *     request.comment()))}
     */
    @PostMapping("/{leaveRequestId}/cancel")
    public ResponseEntity<LeaveRequestResponse> cancel(
            @PathVariable Long leaveRequestId, @Valid @RequestBody LeaveActionRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffLeaveController.class, "cancel(Long,LeaveActionRequest)");
        try {
            return ResponseEntity.ok(staffLeaveService.cancel(leaveRequestId, request.comment()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffLeaveController.class,
                    "cancel(Long,LeaveActionRequest)");
        }
    }
}
