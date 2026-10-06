package com.gokulsweets.restaurant.customer.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Routine updates age out of the attention queue; their history is retained. Financial exceptions do not. */
@Component
@RequiredArgsConstructor
public class CustomerNotificationMaintenance {
    @org.springframework.beans.factory.annotation.Value("${gokul.jobs.worker-enabled:false}")
    private boolean dedicatedImportWorker;

    private final JdbcTemplate jdbc;
    private final CustomerNotificationInbox inbox;
    private final org.springframework.core.env.Environment settings;
    @Scheduled(fixedDelay=300000,initialDelay=300000)
    @Transactional
    public void archiveRoutineUpdates() {
        if(dedicatedImportWorker)return;
        if (!inbox.enabled()) return;
        jdbc.update("""
            UPDATE customer_notification_events SET read_at=CURRENT_TIMESTAMP,auto_acknowledged=FALSE
            WHERE id IN (SELECT id FROM customer_notification_events WHERE read_at IS NULL AND environment=?
                AND kind IN ('CONFIRMED','PAYMENT_PAID','PREPARING','READY_FOR_PICKUP','READY_FOR_DELIVERY',
                             'OUT_FOR_DELIVERY','PICKED_UP','DELIVERED','READY_TIME_CHANGED','PICKUP_WINDOW_EXPIRED','NO_SHOW')
                AND created_at<CURRENT_TIMESTAMP-INTERVAL '7 days' ORDER BY id LIMIT 500 FOR UPDATE SKIP LOCKED)
            """,settings.getProperty("gokul.environment-isolation.environment", ""));
    }
}
