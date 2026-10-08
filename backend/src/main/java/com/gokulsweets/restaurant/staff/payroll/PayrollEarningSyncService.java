package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;
import com.gokulsweets.restaurant.staff.attendance.AttendanceType;
import com.gokulsweets.restaurant.staff.attendance.StaffAttendance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Coordinates payroll earning sync operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollEarningSyncService {

    private final PayrollAttendanceRepository payrollAttendanceRepository;

    private final StaffCompensationProfileRepository compensationRepository;

    private final StaffEarningRepository earningRepository;

    /**
     * Syncs approved attendance.
     *
     * @param staffUserId the staff user id
     * @return the sync approved attendance result
     */
    @Transactional
    public int syncApprovedAttendance(Long staffUserId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PayrollEarningSyncService.class, "syncApprovedAttendance(Long)");
        try {
            List<StaffAttendance> attendanceRecords =
                    payrollAttendanceRepository.findUnsyncedApprovedAttendance(
                            staffUserId, ApprovalRequestStatus.APPROVED);
            int created = 0;
            for (StaffAttendance attendance : attendanceRecords) {
                if (earningRepository.existsByAttendanceId(attendance.getId())) {
                    continue;
                }
                StaffCompensationProfile compensation =
                        compensationRepository
                                .findFirstByStaffUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                        staffUserId, attendance.getAttendanceDate())
                                .orElse(null);
                if (compensation == null) {
                    log.warn(
                            "Approved attendance cannot be converted to earning because no"
                                + " compensation profile exists: attendanceId={}, staffUserId={},"
                                + " attendanceDate={}",
                            attendance.getId(),
                            staffUserId,
                            attendance.getAttendanceDate());
                    continue;
                }
                BigDecimal rate = resolveRate(attendance.getAttendanceType(), compensation);
                StaffEarning earning = new StaffEarning();
                earning.setStaffUser(attendance.getStaffUser());
                earning.setBranch(attendance.getBranch());
                earning.setAttendance(attendance);
                earning.setEarningDate(attendance.getAttendanceDate());
                earning.setAttendanceType(attendance.getAttendanceType());
                earning.setRateSnapshot(rate);
                earning.setAmount(rate);
                earningRepository.save(earning);
                created++;
            }
            return created;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PayrollEarningSyncService.class,
                    "syncApprovedAttendance(Long)");
        }
    }

    /**
     * Resolves rate.
     *
     * @param attendanceType the attendance type
     * @param compensation the compensation
     * @return the resolve rate result
     */
    private BigDecimal resolveRate(
            AttendanceType attendanceType, StaffCompensationProfile compensation) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PayrollEarningSyncService.class,
                        "resolveRate(AttendanceType,StaffCompensationProfile)");
        try {
            return switch (attendanceType) {
                case PRESENT -> compensation.getDailyRate();
                case HALF_DAY -> compensation.getHalfDayRate();
                case ABSENT -> BigDecimal.ZERO;
            };
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PayrollEarningSyncService.class,
                    "resolveRate(AttendanceType,StaffCompensationProfile)");
        }
    }
}
