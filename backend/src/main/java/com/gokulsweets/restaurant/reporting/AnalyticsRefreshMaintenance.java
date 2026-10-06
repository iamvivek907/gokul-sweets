package com.gokulsweets.restaurant.reporting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name={"gokul.reporting.automatic-refresh","gokul.jobs.background-enabled"},havingValue="true")
public class AnalyticsRefreshMaintenance {
    private final AnalyticsRefreshService service;
    @Scheduled(fixedDelayString="${gokul.reporting.refresh-delay-ms:60000}",initialDelayString="${gokul.reporting.initial-delay-ms:10000}")
    public void refresh() {
        try {service.refreshChangedOrders();}
        catch(Exception failure) {log.error("Automatic analytics refresh failed; previous report snapshot retained",failure);}
    }
}
