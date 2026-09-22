package com.gokulsweets.restaurant.order.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(
        prefix = "gokul.orders.preparation"
)
@Getter
@Setter
public class PreparationWindowProperties {

    /*
     * Default preparation window for normal pickup orders.
     */
    private int normalLeadMinutes =
            60;


    /*
     * Priority orders are expected to enter preparation
     * closer to pickup time.
     */
    private int priorityLeadMinutes =
            30;


    /*
     * ADMIN_OVERRIDE describes pickup-capacity handling,
     * not preparation urgency.
     *
     * Therefore it currently follows the normal window.
     */
    private int adminOverrideLeadMinutes =
            60;
}