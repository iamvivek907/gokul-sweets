package com.gokulsweets.restaurant.branch.dto;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.observability.MethodTiming;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Immutable branch response data contract.
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
 * @param coverImageUrl the cover image url
 * @param mobileCoverImageUrl the mobile cover image url
 * @param coverAltText the cover alt text
 * @param description the description
 * @param pickupAvailable the pickup available
 * @param operational the operational
 */
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
     * Maps the supplied data into a {@code BranchResponse} representation.
     *
     * @param branch the branch supplied to this method
     * @param coverImageUrl the cover image url supplied to this method
     * @param mobileCoverImageUrl the mobile cover image url supplied to this method
     * @param coverAltText the cover alt text supplied to this method
     * @param description the description supplied to this method
     * @param pickupAvailable the pickup available supplied to this method
     * @return the {@code BranchResponse} result
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
