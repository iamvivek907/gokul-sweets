package com.gokulsweets.restaurant.security.dto;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.staff.Permission;
import com.gokulsweets.restaurant.staff.StaffUser;

import java.util.Set;
import java.util.stream.Collectors;

public record AdminAuthResponse(
        Long staffId,
        String username,
        String fullName,
        String phone,
        String roleName,
        Set<String> permissions,
        Set<Long> branchIds
) {

    public static AdminAuthResponse from(
            StaffUser staffUser
    ) {

        Set<String> permissions =
                staffUser.getRole()
                        .getPermissions()
                        .stream()
                        .map(Permission::getName)
                        .map(Enum::name)
                        .collect(Collectors.toSet());

        Set<Long> branchIds =
                staffUser.getBranches()
                        .stream()
                        .map(Branch::getId)
                        .collect(Collectors.toSet());

        return new AdminAuthResponse(
                staffUser.getId(),
                staffUser.getUsername(),
                staffUser.getFullName(),
                staffUser.getPhone(),
                staffUser.getRole().getName(),
                permissions,
                branchIds
        );
    }
}