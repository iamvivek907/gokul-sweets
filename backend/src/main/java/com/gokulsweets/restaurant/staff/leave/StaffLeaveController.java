package com.gokulsweets.restaurant.staff.leave;

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

@RestController
@RequestMapping("/api/admin/me/leave-requests")
@RequiredArgsConstructor
public class StaffLeaveController {

    private final StaffLeaveService
            staffLeaveService;


    @GetMapping("/options")
    public ResponseEntity<LeaveOptionsResponse>
    getOptions() {

        return ResponseEntity.ok(
                staffLeaveService
                        .getOptions()
        );
    }


    @GetMapping
    public ResponseEntity<Page<LeaveRequestResponse>>
    getMyLeaveRequests(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size
    ) {

        return ResponseEntity.ok(
                staffLeaveService
                        .getMyLeaveRequests(
                                page,
                                size
                        )
        );
    }


    @GetMapping("/{leaveRequestId}")
    public ResponseEntity<LeaveRequestResponse>
    getMyLeaveRequest(

            @PathVariable
            Long leaveRequestId
    ) {

        return ResponseEntity.ok(
                staffLeaveService
                        .getMyLeaveRequest(
                                leaveRequestId
                        )
        );
    }


    @PostMapping
    public ResponseEntity<LeaveRequestResponse>
    createLeaveRequest(

            @Valid
            @RequestBody
            CreateLeaveRequest request
    ) {

        return ResponseEntity
                .status(
                        HttpStatus.CREATED
                )
                .body(
                        staffLeaveService
                                .createLeaveRequest(
                                        request
                                )
                );
    }


    @PutMapping("/{leaveRequestId}")
    public ResponseEntity<LeaveRequestResponse>
    updateSentBackRequest(

            @PathVariable
            Long leaveRequestId,

            @Valid
            @RequestBody
            UpdateLeaveRequest request
    ) {

        return ResponseEntity.ok(
                staffLeaveService
                        .updateSentBackRequest(
                                leaveRequestId,
                                request
                        )
        );
    }


    @PostMapping("/{leaveRequestId}/resubmit")
    public ResponseEntity<LeaveRequestResponse>
    resubmit(

            @PathVariable
            Long leaveRequestId,

            @Valid
            @RequestBody
            LeaveActionRequest request
    ) {

        return ResponseEntity.ok(
                staffLeaveService
                        .resubmit(
                                leaveRequestId,
                                request.comment()
                        )
        );
    }


    @PostMapping("/{leaveRequestId}/cancel")
    public ResponseEntity<LeaveRequestResponse>
    cancel(

            @PathVariable
            Long leaveRequestId,

            @Valid
            @RequestBody
            LeaveActionRequest request
    ) {

        return ResponseEntity.ok(
                staffLeaveService
                        .cancel(
                                leaveRequestId,
                                request.comment()
                        )
        );
    }
}
