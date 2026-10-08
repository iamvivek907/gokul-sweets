package com.gokulsweets.restaurant.printing.dto;

import java.time.LocalDateTime;

/** Immutable print agent heartbeat response data contract. */
public record PrintAgentHeartbeatResponse(
        String agentId, Long branchId, String station, LocalDateTime serverTime, String status) {}
