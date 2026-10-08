package com.gokulsweets.restaurant.branch.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Immutable admin branch create request data contract.
 *
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
 */
public record AdminBranchCreateRequest(
        @NotBlank
                @Size(max = 50)
                @Pattern(
                        regexp = "^[A-Za-z0-9_-]+$",
                        message =
                                "Branch code may contain only letters, numbers, hyphens and"
                                        + " underscores.")
                String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String address,
        @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Size(max = 10)
                @Pattern(
                        regexp = "^$|^[1-9][0-9]{5}$",
                        message = "Pincode must be a valid 6-digit Indian pincode.")
                String pincode,
        @Size(max = 20)
                @Pattern(
                        regexp = "^$|^(?:\\+91[- ]?)?[6-9][0-9]{9}$",
                        message = "Phone must be a valid Indian mobile number.")
                String phone,
        @Pattern(regexp = "^$|^[0-9]{14}$", message = "FSSAI licence number must be 14 digits.")
                String fssaiLicenceNumber,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalTime openingTime,
        LocalTime closingTime,
        Boolean active) {}
