package com.gokulsweets.restaurant.security;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffAuthorizationService {

    private final StaffUserRepository
            staffUserRepository;

    @Transactional(readOnly = true)
    public StaffUser getCurrentStaff() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(
                authentication.getPrincipal()
        )) {

            throw new AccessDeniedException(
                    "Authentication is required."
            );
        }

        return staffUserRepository
                .findByUsername(
                        authentication.getName()
                )
                .orElseThrow(() -> {

                    log.warn(
                            "Authenticated staff user not found: username={}",
                            authentication.getName()
                    );

                    return new AccessDeniedException(
                            "Staff user does not exist."
                    );
                });
    }

    @Transactional(readOnly = true)
    public void requireBranchAccess(
            Long branchId
    ) {

        if (branchId == null) {

            throw new IllegalArgumentException(
                    "Branch ID is required."
            );
        }

        StaffUser staff =
                getCurrentStaff();

        /*
         * Owner has access to all branches.
         */
        if ("OWNER_ADMIN".equals(
                staff.getRole().getName()
        )) {

            return;
        }

        boolean allowed =
                staff.getBranches()
                        .stream()
                        .anyMatch(branch ->
                                branch.getId()
                                        .equals(
                                                branchId
                                        )
                        );

        if (!allowed) {

            log.warn(
                    "Branch access denied: staffUserId={}, branchId={}",
                    staff.getId(),
                    branchId
            );

            throw new AccessDeniedException(
                    "You do not have access to this branch."
            );
        }
    }

    public void requirePermission(
            PermissionName permission
    ) {

        if (permission == null) {

            throw new IllegalArgumentException(
                    "Permission is required."
            );
        }

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(
                authentication.getPrincipal()
        )) {

            throw new AccessDeniedException(
                    "Authentication is required."
            );
        }

        boolean allowed =
                authentication
                        .getAuthorities()
                        .stream()
                        .anyMatch(authority ->
                                authority
                                        .getAuthority()
                                        .equals(
                                                permission.name()
                                        )
                        );

        if (!allowed) {

            log.warn(
                    "Staff permission denied: username={}, requiredPermission={}",
                    authentication.getName(),
                    permission
            );

            throw new AccessDeniedException(
                    "You do not have permission to perform this action."
            );
        }
    }
}