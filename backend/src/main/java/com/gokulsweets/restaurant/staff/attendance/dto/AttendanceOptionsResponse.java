package com.gokulsweets.restaurant.staff.attendance.dto;

import com.gokulsweets.restaurant.staff.attendance.AttendanceType;

import java.util.List;

public record AttendanceOptionsResponse(

        List<AttendanceBranchOptionResponse> branches,

        List<AttendanceType> attendanceTypes
) {
}
