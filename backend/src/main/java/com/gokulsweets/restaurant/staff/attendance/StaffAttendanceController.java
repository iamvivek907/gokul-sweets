package com.gokulsweets.restaurant.staff.attendance;

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

@RestController
@RequestMapping("/api/admin/me/attendance")
@RequiredArgsConstructor
public class StaffAttendanceController {

    private final StaffAttendanceService
            staffAttendanceService;


    @GetMapping("/options")
    public ResponseEntity<AttendanceOptionsResponse>
    getOptions() {

        return ResponseEntity.ok(
                staffAttendanceService
                        .getOptions()
        );
    }


    @GetMapping
    public ResponseEntity<Page<AttendanceResponse>>
    getMyAttendance(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size
    ) {

        return ResponseEntity.ok(
                staffAttendanceService
                        .getMyAttendance(
                                page,
                                size
                        )
        );
    }


    @GetMapping("/{attendanceId}")
    public ResponseEntity<AttendanceResponse>
    getMyAttendance(

            @PathVariable
            Long attendanceId
    ) {

        return ResponseEntity.ok(
                staffAttendanceService
                        .getMyAttendance(
                                attendanceId
                        )
        );
    }


    @GetMapping("/{attendanceId}/history")
    public ResponseEntity<List<AttendanceApprovalHistoryResponse>>
    getMyAttendanceHistory(

            @PathVariable
            Long attendanceId
    ) {

        return ResponseEntity.ok(
                staffAttendanceService
                        .getMyHistory(
                                attendanceId
                        )
        );
    }


    @PostMapping
    public ResponseEntity<AttendanceResponse>
    createAttendance(

            @Valid
            @RequestBody
            CreateAttendanceRequest request
    ) {

        return ResponseEntity
                .status(
                        HttpStatus.CREATED
                )
                .body(
                        staffAttendanceService
                                .createAttendance(
                                        request
                                )
                );
    }


    @PutMapping("/{attendanceId}")
    public ResponseEntity<AttendanceResponse>
    updateSentBackAttendance(

            @PathVariable
            Long attendanceId,

            @Valid
            @RequestBody
            UpdateAttendanceRequest request
    ) {

        return ResponseEntity.ok(
                staffAttendanceService
                        .updateSentBackAttendance(
                                attendanceId,
                                request
                        )
        );
    }


    @PostMapping("/{attendanceId}/resubmit")
    public ResponseEntity<AttendanceResponse>
    resubmit(

            @PathVariable
            Long attendanceId,

            @Valid
            @RequestBody
            AttendanceActionRequest request
    ) {

        return ResponseEntity.ok(
                staffAttendanceService
                        .resubmit(
                                attendanceId,
                                request
                        )
        );
    }


    @PostMapping("/{attendanceId}/cancel")
    public ResponseEntity<AttendanceResponse>
    cancel(

            @PathVariable
            Long attendanceId,

            @Valid
            @RequestBody
            AttendanceActionRequest request
    ) {

        return ResponseEntity.ok(
                staffAttendanceService
                        .cancel(
                                attendanceId,
                                request
                        )
        );
    }
}
