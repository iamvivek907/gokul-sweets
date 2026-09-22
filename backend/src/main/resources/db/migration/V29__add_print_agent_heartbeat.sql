CREATE TABLE print_agent_heartbeats (
    id BIGSERIAL PRIMARY KEY,

    branch_id BIGINT NOT NULL,
    agent_id VARCHAR(150) NOT NULL,
    station VARCHAR(50) NOT NULL,

    first_seen_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NOT NULL,

    heartbeat_count BIGINT NOT NULL DEFAULT 1,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_print_agent_heartbeat_branch
        FOREIGN KEY (branch_id)
        REFERENCES branches(id),

    CONSTRAINT uq_print_agent_heartbeat_identity
        UNIQUE (branch_id, agent_id, station)
);

CREATE INDEX idx_print_agent_heartbeat_branch_station
    ON print_agent_heartbeats (
        branch_id,
        station,
        last_seen_at DESC
    );

CREATE INDEX idx_print_agent_heartbeat_last_seen
    ON print_agent_heartbeats (
        last_seen_at DESC
    );
