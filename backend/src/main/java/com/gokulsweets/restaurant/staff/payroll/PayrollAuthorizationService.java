package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.StaffUser;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/** Coordinates payroll authorization operations. */
@Service
@RequiredArgsConstructor
public class PayrollAuthorizationService {

    private final StaffAuthorizationService staffAuthorizationService;

    /**
     * Requires view.
     *
     * @return the require view result
     */
    public StaffUser requireView() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PayrollAuthorizationService.class, "requireView()");
        try {
            requireAuthority("PAYROLL_VIEW");
            return staffAuthorizationService.getCurrentStaff();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PayrollAuthorizationService.class, "requireView()");
        }
    }

    /**
     * Requires manage.
     *
     * @return the require manage result
     */
    public StaffUser requireManage() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PayrollAuthorizationService.class, "requireManage()");
        try {
            requireAuthority("PAYROLL_MANAGE");
            return staffAuthorizationService.getCurrentStaff();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PayrollAuthorizationService.class,
                    "requireManage()");
        }
    }

    /**
     * Requires target scope.
     *
     * @param actor the actor
     * @param target the target
     */
    public void requireTargetScope(StaffUser actor, StaffUser target) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PayrollAuthorizationService.class,
                        "requireTargetScope(StaffUser,StaffUser)");
        try {
            if (isOwner(actor)) {
                return;
            }
            if (isOwner(target)) {
                throw new AccessDeniedException("Only an owner can manage owner payroll.");
            }
            Set<Long> actorBranchIds =
                    actor.getBranches().stream()
                            .map(branch -> branch.getId())
                            .collect(Collectors.toSet());
            Set<Long> targetBranchIds =
                    target.getBranches().stream()
                            .map(branch -> branch.getId())
                            .collect(Collectors.toSet());
            if (targetBranchIds.isEmpty() || !actorBranchIds.containsAll(targetBranchIds)) {
                throw new AccessDeniedException("This staff member is outside your payroll scope.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PayrollAuthorizationService.class,
                    "requireTargetScope(StaffUser,StaffUser)");
        }
    }

    /**
     * Requires authority.
     *
     * @param authority the authority
     */
    private void requireAuthority(String authority) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PayrollAuthorizationService.class, "requireAuthority(String)");
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            boolean granted =
                    authentication != null
                            && authentication.isAuthenticated()
                            && authentication.getAuthorities().stream()
                                    .anyMatch(item -> authority.equals(item.getAuthority()));
            if (!granted) {
                throw new AccessDeniedException("Required payroll permission is missing.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PayrollAuthorizationService.class,
                    "requireAuthority(String)");
        }
    }

    /**
     * Reports whether owner.
     *
     * @param staff the staff
     * @return the is owner result
     */
    private boolean isOwner(StaffUser staff) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PayrollAuthorizationService.class, "isOwner(StaffUser)");
        try {
            return staff.getRole() != null && "OWNER_ADMIN".equals(staff.getRole().getName());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PayrollAuthorizationService.class,
                    "isOwner(StaffUser)");
        }
    }
}
