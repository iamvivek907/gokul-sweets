package com.gokulsweets.restaurant.printing.repository;

import com.gokulsweets.restaurant.printing.entity.PrintAgentHeartbeat;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PrintAgentHeartbeatRepository
        extends JpaRepository<PrintAgentHeartbeat, Long> {

    @Modifying
    @Query(
            value = """
                    INSERT INTO print_agent_heartbeats (
                        branch_id,
                        agent_id,
                        station,
                        first_seen_at,
                        last_seen_at,
                        heartbeat_count,
                        created_at,
                        updated_at
                    )
                    VALUES (
                        :branchId,
                        :agentId,
                        :station,
                        :heartbeatAt,
                        :heartbeatAt,
                        1,
                        :heartbeatAt,
                        :heartbeatAt
                    )
                    ON CONFLICT (
                        branch_id,
                        agent_id,
                        station
                    )
                    DO UPDATE SET
                        last_seen_at = EXCLUDED.last_seen_at,
                        heartbeat_count =
                            print_agent_heartbeats.heartbeat_count + 1,
                        updated_at = EXCLUDED.updated_at
                    """,
            nativeQuery = true
    )
    void upsertHeartbeat(
            @Param("branchId")
            Long branchId,

            @Param("agentId")
            String agentId,

            @Param("station")
            String station,

            @Param("heartbeatAt")
            LocalDateTime heartbeatAt
    );


    Optional<PrintAgentHeartbeat>
    findFirstByBranchIdAndStationOrderByLastSeenAtDesc(
            Long branchId,
            PrinterStation station
    );


    List<PrintAgentHeartbeat>
    findByBranchIdOrderByLastSeenAtDesc(
            Long branchId
    );
}
