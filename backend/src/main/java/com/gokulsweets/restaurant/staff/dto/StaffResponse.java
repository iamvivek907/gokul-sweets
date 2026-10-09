package com.gokulsweets.restaurant.staff.dto;

import java.util.Set;

/**
 * Immutable staff response data contract.
 *
 * @param id the id
 * @param username the username
 * @param fullName the full name
 * @param phone the phone
 * @param active the active
 * @param roleName the role name
 * @param branchIds the branch ids
 */
public record StaffResponse(
        Long id,
        String username,
        String fullName,
        String phone,
        boolean active,
        String roleName,
        Set<Long> branchIds) {}
