package com.gokulsweets.restaurant.inventory.scheduler;

import com.gokulsweets.restaurant.inventory.config.InventoryProperties;
import com.gokulsweets.restaurant.inventory.enums.InventoryReservationStatus;
import com.gokulsweets.restaurant.inventory.repository.InventoryReservationRepository;
import com.gokulsweets.restaurant.inventory.service.InventoryReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryHoldExpiryScheduler {

    private final InventoryReservationRepository reservationRepository;
    private final InventoryReservationService reservationService;
    private final InventoryProperties properties;
    private final Clock inventoryClock;

    @Scheduled(
            fixedDelayString =
                    "${inventory.hold-expiry-check-milliseconds:30000}"
    )
    public void releaseExpiredHolds() {
        List<String> reservationKeys =
                reservationRepository.findExpiredHoldKeys(
                        InventoryReservationStatus.TEMPORARY_HOLD,
                        LocalDateTime.now(inventoryClock),
                        PageRequest.of(
                                0,
                                properties.getExpiryBatchSize()
                        )
                );

        int released = 0;

        for (String reservationKey : reservationKeys) {
            try {
                if (reservationService.expireHoldIfDue(reservationKey)) {
                    released++;
                }
            } catch (RuntimeException exception) {
                log.error(
                        "Unable to expire inventory hold: reservationKey={}",
                        mask(reservationKey),
                        exception
                );
            }
        }

        if (released > 0) {
            log.info(
                    "Expired inventory holds released: count={}",
                    released
            );
        }
    }

    private String mask(String value) {
        if (value == null || value.length() <= 8) {
            return "***";
        }
        return value.substring(0, 8) + "...";
    }
}
