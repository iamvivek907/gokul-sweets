package com.gokulsweets.restaurant.delivery;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeliveryRiderHoldExpiryScheduler {
    private final DeliveryRiderHoldService holds;

    @Scheduled(fixedDelayString = "${delivery.rider-holds.expiry-check-milliseconds:60000}")
    public void expire() {
        // Cleanup remains active after the feature is switched off, so old reservations cannot leak capacity.
        for (String key : holds.expiredKeys(100)) holds.expire(key);
    }
}
