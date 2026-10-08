package com.gokulsweets.restaurant.inventory.config;

import com.gokulsweets.restaurant.config.ApplicationClock;
import com.gokulsweets.restaurant.observability.MethodTiming;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/** Backend inventory configuration contract and implementation. */
@Configuration
@EnableScheduling
public class InventoryConfiguration {

    /**
     * Inventory clock.
     *
     * @param businessClock the business clock
     * @return the inventory clock result
     */
    @Bean
    public Clock inventoryClock(ApplicationClock businessClock) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        InventoryConfiguration.class, "inventoryClock(ApplicationClock)");
        try {
            return Clock.system(businessClock.zone());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryConfiguration.class,
                    "inventoryClock(ApplicationClock)");
        }
    }
}
