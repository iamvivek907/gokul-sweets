package com.gokulsweets.restaurant.staff.attendance.dto;

import com.gokulsweets.restaurant.staff.attendance.AttendanceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateAttendanceRequest(

        @NotNull
        Long branchId,

        @NotNull
        LocalDate attendanceDate,

        @NotNull
        AttendanceType attendanceType,

        LocalTime checkInTime,

        LocalTime checkOutTime,

        @Size(max = 1000)
        String note
) {
}
