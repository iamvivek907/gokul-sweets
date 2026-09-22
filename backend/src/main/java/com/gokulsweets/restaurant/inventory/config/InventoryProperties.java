package com.gokulsweets.restaurant.inventory.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "inventory")
@Validated
@Getter
@Setter
public class InventoryProperties {

    /*
     * Safe rollout switch.
     *
     * Keep false until policies and daily allocations have
     * been configured for the products sold online.
     */
    private boolean enforcementEnabled = false;

    @Min(1)
    @Max(120)
    private int temporaryHoldMinutes = 15;

    @Min(1)
    @Max(365)
    private int defaultBookingHorizonDays = 14;

    @Min(1)
    @Max(1000)
    private int expiryBatchSize = 100;

    @Min(1000)
    private long holdExpiryCheckMilliseconds = 30000;

    private String businessZone = "Asia/Kolkata";
}
