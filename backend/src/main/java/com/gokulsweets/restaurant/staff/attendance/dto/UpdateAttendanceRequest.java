package com.gokulsweets.restaurant.staff.attendance.dto;

import com.gokulsweets.restaurant.staff.attendance.AttendanceType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Immutable update attendance request data contract.
 *
 * @param branchId the branch id
 * @param attendanceDate the attendance date
 * @param attendanceType the attendance type
 * @param checkInTime the check in time
 * @param checkOutTime the check out time
 * @param note the note
 */
public record UpdateAttendanceRequest(
        @NotNull Long branchId,
        @NotNull LocalDate attendanceDate,
        @NotNull AttendanceType attendanceType,
        LocalTime checkInTime,
        LocalTime checkOutTime,
        @Size(max = 1000) String note) {}
