-- One managed process and command mailbox per branch/station. No credentials are stored here.
CREATE TABLE print_station_controls (
 branch_id BIGINT NOT NULL REFERENCES branches(id),
 station VARCHAR(50) NOT NULL,
 agent_id VARCHAR(120) NOT NULL,
 profile TEXT NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT FALSE,
 runtime TEXT NOT NULL DEFAULT '{}',
 last_seen_at TIMESTAMP,
 command_id VARCHAR(36),
 command TEXT,
 command_result TEXT,
 PRIMARY KEY (branch_id, station)
);
