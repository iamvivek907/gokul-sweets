package com.gokulsweets.restaurant.maintenance;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataCleanupScheduler {
    private static final Logger log=LoggerFactory.getLogger(DataCleanupScheduler.class);
    private final DataCleanupService cleanup;
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void poll() {
        try {cleanup.scheduledRun();} catch(RuntimeException failure) {log.warn("Daily cleanup polling failed",failure);}
    }
}
