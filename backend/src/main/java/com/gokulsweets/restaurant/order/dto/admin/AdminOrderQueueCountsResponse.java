package com.gokulsweets.restaurant.order.dto.admin;

import java.time.LocalDateTime;

public record AdminOrderQueueCountsResponse(

        long overdue,

        long eligible,

        long scheduled,

        long preparing,

        long ready,

        long confirmedTotal,

        long actionableTotal,

        LocalDateTime generatedAt

) {
}