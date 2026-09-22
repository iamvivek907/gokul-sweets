package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PayrollAuthorizationService {

    private final StaffAuthorizationService
            staffAuthorizationService;


    public StaffUser requireView() {

        requireAuthority(
                "PAYROLL_VIEW"
        );

        return staffAuthorizationService
                .getCurrentStaff();
    }


    public StaffUser requireManage() {

        requireAuthority(
                "PAYROLL_MANAGE"
        );

        return staffAuthorizationService
                .getCurrentStaff();
    }


    public void requireTargetScope(
            StaffUser actor,
            StaffUser target
    ) {

        if (
                isOwner(
                        actor
                )
        ) {

            return;
        }

        if (
                isOwner(
                        target
                )
        ) {

            throw new AccessDeniedException(
                    "Only an owner can manage owner payroll."
            );
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
                        ||
                        !actorBranchIds.containsAll(
                                targetBranchIds
                        )
        ) {

            throw new AccessDeniedException(
                    "This staff member is outside your payroll scope."
            );
        }
    }


    private void requireAuthority(
            String authority
    ) {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        boolean granted =
                authentication != null
                        &&
                        authentication.isAuthenticated()
                        &&
                        authentication.getAuthorities()
                                .stream()
                                .anyMatch(
                                        item ->
                                                authority.equals(
                                                        item.getAuthority()
                                                )
                                );

        if (!granted) {

            throw new AccessDeniedException(
                    "Required payroll permission is missing."
            );
        }
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
