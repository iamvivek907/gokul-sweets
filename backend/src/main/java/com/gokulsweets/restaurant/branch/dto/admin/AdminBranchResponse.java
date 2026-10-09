package com.gokulsweets.restaurant.branch.dto.admin;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.observability.MethodTiming;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Immutable admin branch response data contract.
 *
 * @param id the id
 * @param code the code
 * @param name the name
 * @param address the address
 * @param city the city
 * @param state the state
 * @param pincode the pincode
 * @param phone the phone
 * @param fssaiLicenceNumber the fssai licence number
 * @param latitude the latitude
 * @param longitude the longitude
 * @param openingTime the opening time
 * @param closingTime the closing time
 * @param active the active
 * @param createdAt the created at
 * @param updatedAt the updated at
 */
public record AdminBranchResponse(
        Long id,
        String code,
        String name,
        String address,
        String city,
        String state,
        String pincode,
        String phone,
        String fssaiLicenceNumber,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalTime openingTime,
        LocalTime closingTime,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Maps the supplied data into a {@code AdminBranchResponse} representation.
     *
     * @param branch the branch supplied to this method
     * @return the {@code AdminBranchResponse} result
     */
    public static AdminBranchResponse from(Branch branch) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminBranchResponse.class, "from(Branch)");
        try {
            return new AdminBranchResponse(
                    branch.getId(),
                    branch.getCode(),
                    branch.getName(),
                    branch.getAddress(),
                    branch.getCity(),
                    branch.getState(),
                    branch.getPincode(),
                    branch.getPhone(),
                    branch.getFssaiLicenceNumber(),
                    branch.getLatitude(),
                    branch.getLongitude(),
                    branch.getOpeningTime(),
                    branch.getClosingTime(),
                    branch.isActive(),
                    branch.getCreatedAt(),
                    branch.getUpdatedAt());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminBranchResponse.class, "from(Branch)");
        }
    }
}
