package com.gokulsweets.restaurant.branch.dto;

import com.gokulsweets.restaurant.branch.Branch;

import java.math.BigDecimal;
import java.time.LocalTime;

public record BranchResponse(
        Long id,
        String code,
        String name,
        String address,
        String city,
        String state,
        String pincode,
        String phone,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalTime openingTime,
        LocalTime closingTime,
        boolean active
) {

    public static BranchResponse from(Branch branch) {
        return new BranchResponse(
                branch.getId(),
                branch.getCode(),
                branch.getName(),
                branch.getAddress(),
                branch.getCity(),
                branch.getState(),
                branch.getPincode(),
                branch.getPhone(),
                branch.getLatitude(),
                branch.getLongitude(),
                branch.getOpeningTime(),
                branch.getClosingTime(),
                branch.isActive()
        );
    }
}