package com.gokulsweets.restaurant.occasion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OccasionHoldExpiryScheduler {
    @org.springframework.beans.factory.annotation.Value("${gokul.jobs.worker-enabled:false}")
    private boolean dedicatedImportWorker;

    private final OccasionCommitmentService commitments;

    @Scheduled(fixedDelayString = "${gokul.occasion.hold-expiry-check-milliseconds:30000}")
    public void releaseDueHolds() {
        if(dedicatedImportWorker)return;
        try {
            commitments.expireDue();
        } catch (RuntimeException failure) {
            log.error("Occasion hold expiry failed; the next scheduler run will retry", failure);
        }
    }
}
