package com.gokulsweets.restaurant.inventory.automation.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "inventory.automation")
@Validated
@Getter
@Setter
public class InventoryAutomationProperties {
    private boolean schedulerEnabled = false;

    @Min(1)
    @Max(365)
    private int maximumRunDays = 60;

    private String systemActor = "SYSTEM_INVENTORY_AUTOMATION";
}
