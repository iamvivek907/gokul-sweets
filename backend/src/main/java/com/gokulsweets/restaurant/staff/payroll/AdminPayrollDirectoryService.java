package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.staff.StaffUserRepository;
import com.gokulsweets.restaurant.staff.payroll.dto.AdminPayrollStaffOptionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminPayrollDirectoryService {

    private final StaffUserRepository
            staffUserRepository;

    private final PayrollAuthorizationService
            payrollAuthorizationService;


    @Transactional(readOnly = true)
    public List<AdminPayrollStaffOptionResponse> getStaffOptions() {

        StaffUser actor =
                payrollAuthorizationService
                        .requireView();

        List<StaffUser> staffUsers =
                staffUserRepository
                        .findAllWithRoleAndBranches();

        return staffUsers
                .stream()
                .filter(
                        target ->
                                canViewTarget(
                                        actor,
                                        target
                                )
                )
                .sorted(
                        Comparator
                                .comparing(
                                        StaffUser::getFullName,
                                        String.CASE_INSENSITIVE_ORDER
                                )
                                .thenComparing(
                                        StaffUser::getId
                                )
                )
                .map(
                        target ->
                                new AdminPayrollStaffOptionResponse(
                                        target.getId(),
                                        target.getUsername(),
                                        target.getFullName(),
                                        target.getRole()
                                                .getName(),
                                        target.isActive()
                                )
                )
                .toList();
    }


    private boolean canViewTarget(
            StaffUser actor,
            StaffUser target
    ) {

        if (
                isOwner(
                        actor
                )
        ) {

            return true;
        }

        if (
                isOwner(
                        target
                )
        ) {

            return false;
        }

        Set<Long> actorBranchIds =
                actor.getBranches()
                        .stream()
                        .map(
                                branch ->
                                        branch.getId()
                        )
                        .collect(
                                Collectors.toSet()
                        );

        Set<Long> targetBranchIds =
                target.getBranches()
                        .stream()
                        .map(
                                branch ->
                                        branch.getId()
                        )
                        .collect(
                                Collectors.toSet()
                        );

        if (
                targetBranchIds.isEmpty()
        ) {

            return false;
        }

        return actorBranchIds
                .containsAll(
                        targetBranchIds
                );
    }


    private boolean isOwner(
            StaffUser staff
    ) {

        return staff.getRole()
                != null
                &&
                "OWNER_ADMIN".equals(
                        staff.getRole()
                                .getName()
                );
    }
}
