package com.gokulsweets.restaurant.printing.dto;

import java.time.LocalDateTime;

/**
 * Immutable print agent heartbeat response data contract.
 *
 * @param agentId the agent id
 * @param branchId the branch id
 * @param station the station
 * @param serverTime the server time
 * @param status the status
 */
public record PrintAgentHeartbeatResponse(
        String agentId, Long branchId, String station, LocalDateTime serverTime, String status) {}
