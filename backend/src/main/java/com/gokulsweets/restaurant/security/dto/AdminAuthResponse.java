package com.gokulsweets.restaurant.security.dto;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.staff.Permission;
import com.gokulsweets.restaurant.staff.StaffUser;

import java.util.Set;
import java.util.stream.Collectors;

/** Immutable admin auth response data contract. */
public record AdminAuthResponse(
        Long staffId,
        String username,
        String fullName,
        String phone,
        String roleName,
        Set<String> permissions,
        Set<Long> branchIds) {

    /**
     * Froms the operation.
     *
     * @param staffUser the staff user
     * @return the from result
     */
    public static AdminAuthResponse from(StaffUser staffUser) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminAuthResponse.class, "from(StaffUser)");
        try {
            Set<String> permissions =
                    staffUser.getRole().getPermissions().stream()
                            .map(Permission::getName)
                            .map(Enum::name)
                            .collect(Collectors.toSet());
            Set<Long> branchIds =
                    staffUser.getBranches().stream().map(Branch::getId).collect(Collectors.toSet());
            return new AdminAuthResponse(
                    staffUser.getId(),
                    staffUser.getUsername(),
                    staffUser.getFullName(),
                    staffUser.getPhone(),
                    staffUser.getRole().getName(),
                    permissions,
                    branchIds);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminAuthResponse.class, "from(StaffUser)");
        }
    }
}
