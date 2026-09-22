package com.gokulsweets.restaurant.printing.dto;

import java.time.LocalDateTime;

public record PrintAgentHeartbeatResponse(

        String agentId,

        Long branchId,

        String station,

        LocalDateTime serverTime,

        String status
) {
}