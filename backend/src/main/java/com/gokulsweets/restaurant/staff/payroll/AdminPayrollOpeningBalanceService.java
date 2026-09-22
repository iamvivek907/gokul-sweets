package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.staff.StaffUserRepository;
import com.gokulsweets.restaurant.staff.payroll.dto.PayrollOpeningBalanceResponse;
import com.gokulsweets.restaurant.staff.payroll.dto.SetPayrollOpeningBalanceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPayrollOpeningBalanceService {

    private final StaffUserRepository
            staffUserRepository;

    private final StaffPayrollOpeningBalanceRepository
            openingBalanceRepository;

    private final PayrollAuthorizationService
            payrollAuthorizationService;

    private final PayrollStaffLockRepository
            staffLockRepository;


    @Transactional(readOnly = true)
    public PayrollOpeningBalanceResponse getOpeningBalance(
            Long staffUserId
    ) {

        StaffUser actor =
                payrollAuthorizationService
                        .requireView();

        StaffUser target =
                getTarget(
                        staffUserId
                );

        payrollAuthorizationService
                .requireTargetScope(
                        actor,
                        target
                );

        return openingBalanceRepository
                .findByStaffUserId(
                        staffUserId
                )
                .map(
                        this::toResponse
                )
                .orElse(
                        null
                );
    }


    @Transactional
    public PayrollOpeningBalanceResponse createOpeningBalance(
            Long staffUserId,
            SetPayrollOpeningBalanceRequest request
    ) {

        StaffUser actor =
                payrollAuthorizationService
                        .requireManage();

        StaffUser target =
                getTarget(
                        staffUserId
                );

        payrollAuthorizationService
                .requireTargetScope(
                        actor,
                        target
                );

        staffLockRepository
                .findByIdForUpdate(
                        target.getId()
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Staff user does not exist."
                                )
                );

        if (
                openingBalanceRepository
                        .existsByStaffUserId(
                                target.getId()
                        )
        ) {

            throw new IllegalStateException(
                    "Opening payroll balance has already been configured for this staff member."
            );
        }

        BigDecimal earned =
                money(
                        request.earnedAmount()
                );

        BigDecimal taken =
                money(
                        request.takenAmount()
                );

        if (
                taken.compareTo(
                        earned
                ) > 0
        ) {

            throw new IllegalArgumentException(
                    "Previously taken amount cannot be greater than previously earned amount."
            );
        }

        StaffPayrollOpeningBalance opening =
                new StaffPayrollOpeningBalance();

        opening.setStaffUser(
                target
        );

        opening.setAsOfDate(
                request.asOfDate()
        );

        opening.setEarnedAmount(
                earned
        );

        opening.setTakenAmount(
                taken
        );

        opening.setNote(
                normalizeNullable(
                        request.note()
                )
        );

        opening.setCreatedByStaffUser(
                actor
        );

        StaffPayrollOpeningBalance saved =
                openingBalanceRepository
                        .save(
                                opening
                        );

        log.info(
                "Payroll opening balance created: openingBalanceId={}, staffUserId={}, asOfDate={}, earnedAmount={}, takenAmount={}, createdByStaffUserId={}",
                saved.getId(),
                target.getId(),
                saved.getAsOfDate(),
                saved.getEarnedAmount(),
                saved.getTakenAmount(),
                actor.getId()
        );

        return toResponse(
                saved
        );
    }


    private StaffUser getTarget(
            Long staffUserId
    ) {

        return staffUserRepository
                .findDetailedById(
                        staffUserId
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Staff user does not exist."
                                )
                );
    }


    private PayrollOpeningBalanceResponse toResponse(
            StaffPayrollOpeningBalance opening
    ) {

        BigDecimal net =
                opening.getEarnedAmount()
                        .subtract(
                                opening.getTakenAmount()
                        );

        return new PayrollOpeningBalanceResponse(
                opening.getId(),
                opening.getStaffUser()
                        .getId(),
                opening.getAsOfDate(),
                opening.getEarnedAmount(),
                opening.getTakenAmount(),
                money(
                        net
                ),
                opening.getNote(),
                opening.getCreatedByStaffUser()
                        .getFullName(),
                opening.getCreatedAt()
        );
    }


    private BigDecimal money(
            BigDecimal value
    ) {

        return (
                value == null
                        ? BigDecimal.ZERO
                        : value
        ).setScale(
                2,
                RoundingMode.HALF_UP
        );
    }


    private String normalizeNullable(
            String value
    ) {

        if (
                value == null
                        ||
                        value.isBlank()
        ) {

            return null;
        }

        return value.trim();
    }
}
