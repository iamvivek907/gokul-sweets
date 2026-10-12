package com.gokulsweets.restaurant.staff.leave;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.staff.leave.dto.LeaveApprovalHistoryResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP endpoints for staff leave history operations. */
@RestController
@RequestMapping("/api/admin/me/leave-requests")
@RequiredArgsConstructor
public class StaffLeaveHistoryController {

    private final StaffLeaveHistoryService staffLeaveHistoryService;

    /**
     * Returns history.
     *
     * @param leaveRequestId the leave request id
     * @return the get history result
     */
    @GetMapping("/{leaveRequestId}/history")
    public ResponseEntity<List<LeaveApprovalHistoryResponse>> getHistory(
            @PathVariable Long leaveRequestId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffLeaveHistoryController.class, "getHistory(Long)");
        try {
            return ResponseEntity.ok(
                    staffLeaveHistoryService.getMyLeaveApprovalHistory(leaveRequestId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffLeaveHistoryController.class,
                    "getHistory(Long)");
        }
    }
}
