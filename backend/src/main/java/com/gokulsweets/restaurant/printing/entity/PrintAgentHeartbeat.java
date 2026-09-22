package com.gokulsweets.restaurant.printing.entity;

import com.gokulsweets.restaurant.printing.enums.PrinterStation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "print_agent_heartbeats",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_print_agent_heartbeat_identity",
                        columnNames = {
                                "branch_id",
                                "agent_id",
                                "station"
                        }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class PrintAgentHeartbeat {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;


    @Column(
            name = "branch_id",
            nullable = false
    )
    private Long branchId;


    @Column(
            name = "agent_id",
            nullable = false,
            length = 150
    )
    private String agentId;


    @Enumerated(
            EnumType.STRING
    )
    @Column(
            name = "station",
            nullable = false,
            length = 50
    )
    private PrinterStation station;


    @Column(
            name = "first_seen_at",
            nullable = false
    )
    private LocalDateTime firstSeenAt;


    @Column(
            name = "last_seen_at",
            nullable = false
    )
    private LocalDateTime lastSeenAt;


    @Column(
            name = "heartbeat_count",
            nullable = false
    )
    private Long heartbeatCount;


    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;


    @Column(
            name = "updated_at",
            nullable = false
    )
    private LocalDateTime updatedAt;
}
