package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;
import com.gokulsweets.restaurant.staff.attendance.AttendanceType;
import com.gokulsweets.restaurant.staff.attendance.StaffAttendance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollEarningSyncService {

    private final PayrollAttendanceRepository
            payrollAttendanceRepository;

    private final StaffCompensationProfileRepository
            compensationRepository;

    private final StaffEarningRepository
            earningRepository;


    @Transactional
    public int syncApprovedAttendance(
            Long staffUserId
    ) {

        List<StaffAttendance> attendanceRecords =
                payrollAttendanceRepository
                        .findUnsyncedApprovedAttendance(
                                staffUserId,
                                ApprovalRequestStatus.APPROVED
                        );

        int created =
                0;

        for (
                StaffAttendance attendance
                : attendanceRecords
        ) {

            if (
                    earningRepository
                            .existsByAttendanceId(
                                    attendance.getId()
                            )
            ) {

                continue;
            }

            StaffCompensationProfile compensation =
                    compensationRepository
                            .findFirstByStaffUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                    staffUserId,
                                    attendance.getAttendanceDate()
                            )
                            .orElse(
                                    null
                            );

            if (
                    compensation == null
            ) {

                log.warn(
                        "Approved attendance cannot be converted to earning because no compensation profile exists: attendanceId={}, staffUserId={}, attendanceDate={}",
                        attendance.getId(),
                        staffUserId,
                        attendance.getAttendanceDate()
                );

                continue;
            }

            BigDecimal rate =
                    resolveRate(
                            attendance.getAttendanceType(),
                            compensation
                    );

            StaffEarning earning =
                    new StaffEarning();

            earning.setStaffUser(
                    attendance.getStaffUser()
            );

            earning.setBranch(
                    attendance.getBranch()
            );

            earning.setAttendance(
                    attendance
            );

            earning.setEarningDate(
                    attendance.getAttendanceDate()
            );

            earning.setAttendanceType(
                    attendance.getAttendanceType()
            );

            earning.setRateSnapshot(
                    rate
            );

            earning.setAmount(
                    rate
            );

            earningRepository.save(
                    earning
            );

            created++;
        }

        return created;
    }


    private BigDecimal resolveRate(
            AttendanceType attendanceType,
            StaffCompensationProfile compensation
    ) {

        return switch (attendanceType) {

            case PRESENT ->
                    compensation.getDailyRate();

            case HALF_DAY ->
                    compensation.getHalfDayRate();

            case ABSENT ->
                    BigDecimal.ZERO;
        };
    }
}
