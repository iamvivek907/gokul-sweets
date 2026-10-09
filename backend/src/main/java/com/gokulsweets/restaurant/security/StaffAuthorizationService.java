package com.gokulsweets.restaurant.security;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.staff.StaffUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates staff authorization operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaffAuthorizationService {

    private final StaffUserRepository staffUserRepository;

    /**
     * Returns current staff.
     *
     * @return the get current staff result
     */
    @Transactional(readOnly = true)
    public StaffUser getCurrentStaff() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAuthorizationService.class, "getCurrentStaff()");
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null
                    || !authentication.isAuthenticated()
                    || "anonymousUser".equals(authentication.getPrincipal())) {
                throw new AccessDeniedException("Authentication is required.");
            }
            return staffUserRepository
                    .findByUsername(authentication.getName())
                    .orElseThrow(
                            () -> {
                                log.warn(
                                        "Authenticated staff user not found: username={}",
                                        authentication.getName());
                                return new AccessDeniedException("Staff user does not exist.");
                            });
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAuthorizationService.class,
                    "getCurrentStaff()");
        }
    }

    /**
     * Requires branch access.
     *
     * @param branchId the branch id
     */
    @Transactional(readOnly = true)
    public void requireBranchAccess(Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAuthorizationService.class, "requireBranchAccess(Long)");
        try {
            if (branchId == null) {
                throw new IllegalArgumentException("Branch ID is required.");
            }
            StaffUser staff = getCurrentStaff();
            /*
             * Owner has access to all branches.
             */
            if ("OWNER_ADMIN".equals(staff.getRole().getName())) {
                return;
            }
            boolean allowed =
                    staff.getBranches().stream()
                            .anyMatch(branch -> branch.getId().equals(branchId));
            if (!allowed) {
                log.warn(
                        "Branch access denied: staffUserId={}, branchId={}",
                        staff.getId(),
                        branchId);
                throw new AccessDeniedException("You do not have access to this branch.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAuthorizationService.class,
                    "requireBranchAccess(Long)");
        }
    }

    /**
     * Requires permission.
     *
     * @param permission the permission
     */
    public void requirePermission(PermissionName permission) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffAuthorizationService.class, "requirePermission(PermissionName)");
        try {
            if (permission == null) {
                throw new IllegalArgumentException("Permission is required.");
            }
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null
                    || !authentication.isAuthenticated()
                    || "anonymousUser".equals(authentication.getPrincipal())) {
                throw new AccessDeniedException("Authentication is required.");
            }
            boolean allowed =
                    authentication.getAuthorities().stream()
                            .anyMatch(
                                    authority ->
                                            authority.getAuthority().equals(permission.name()));
            if (!allowed) {
                log.warn(
                        "Staff permission denied: username={}, requiredPermission={}",
                        authentication.getName(),
                        permission);
                throw new AccessDeniedException(
                        "You do not have permission to perform this action.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAuthorizationService.class,
                    "requirePermission(PermissionName)");
        }
    }
}
