package com.gokulsweets.restaurant.branch.dto;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.observability.MethodTiming;

import java.math.BigDecimal;
import java.time.LocalTime;

/** Immutable branch response data contract. */
public record BranchResponse(
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
        String coverImageUrl,
        String mobileCoverImageUrl,
        String coverAltText,
        String description,
        boolean pickupAvailable,
        boolean operational) {

    /**
     * Froms the operation.
     *
     * @param branch the branch
     * @param coverImageUrl the cover image url
     * @param mobileCoverImageUrl the mobile cover image url
     * @param coverAltText the cover alt text
     * @param description the description
     * @param pickupAvailable the pickup available
     * @return the from result
     */
    public static BranchResponse from(
            Branch branch,
            String coverImageUrl,
            String mobileCoverImageUrl,
            String coverAltText,
            String description,
            boolean pickupAvailable) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        BranchResponse.class, "from(Branch,String,String,String,String,boolean)");
        try {
            return new BranchResponse(
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
                    coverImageUrl,
                    mobileCoverImageUrl,
                    coverAltText,
                    description,
                    pickupAvailable,
                    branch.isOperational());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchResponse.class,
                    "from(Branch,String,String,String,String,boolean)");
        }
    }
}
