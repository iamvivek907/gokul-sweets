package com.gokulsweets.restaurant.branch.dto.admin;

import com.gokulsweets.restaurant.branch.Branch;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record AdminBranchResponse(

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

        boolean active,

        LocalDateTime createdAt,

        LocalDateTime updatedAt
) {

    public static AdminBranchResponse from(
            Branch branch
    ) {

        return new AdminBranchResponse(

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

                branch.isActive(),

                branch.getCreatedAt(),

                branch.getUpdatedAt()
        );
    }
}