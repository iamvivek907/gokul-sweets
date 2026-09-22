package com.gokulsweets.restaurant.staff.leave;

import com.gokulsweets.restaurant.staff.leave.dto.LeaveApprovalHistoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/admin/me/leave-requests"
)
@RequiredArgsConstructor
public class StaffLeaveHistoryController {

    private final StaffLeaveHistoryService
            staffLeaveHistoryService;


    @GetMapping(
            "/{leaveRequestId}/history"
    )
    public ResponseEntity<List<LeaveApprovalHistoryResponse>>
    getHistory(

            @PathVariable
            Long leaveRequestId
    ) {

        return ResponseEntity.ok(
                staffLeaveHistoryService
                        .getMyLeaveApprovalHistory(
                                leaveRequestId
                        )
        );
    }
}
