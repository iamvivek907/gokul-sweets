package com.gokulsweets.restaurant.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import com.gokulsweets.restaurant.config.ApplicationClock;

@Configuration
@EnableScheduling
public class InventoryConfiguration {

    @Bean
    public Clock inventoryClock(ApplicationClock businessClock) {
        return Clock.system(businessClock.zone());
    }
}
