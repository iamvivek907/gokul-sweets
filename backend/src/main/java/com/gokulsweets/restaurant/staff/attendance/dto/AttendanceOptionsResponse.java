package com.gokulsweets.restaurant.staff.attendance.dto;

import com.gokulsweets.restaurant.staff.attendance.AttendanceType;

import java.util.List;

/** Immutable attendance options response data contract. */
public record AttendanceOptionsResponse(
        List<AttendanceBranchOptionResponse> branches, List<AttendanceType> attendanceTypes) {}
