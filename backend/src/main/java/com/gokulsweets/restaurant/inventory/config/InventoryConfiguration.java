package com.gokulsweets.restaurant.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
public class InventoryConfiguration {

    @Bean
    public Clock inventoryClock(
            InventoryProperties properties
    ) {

        return Clock.system(
                ZoneId.of(
                        properties.getBusinessZone()
                )
        );
    }
}
