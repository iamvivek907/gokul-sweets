CREATE TABLE printer_devices (
    id BIGSERIAL PRIMARY KEY,

    branch_id BIGINT NOT NULL,

    code VARCHAR(50) NOT NULL,

    name VARCHAR(120) NOT NULL,

    station VARCHAR(50) NOT NULL,

    protocol VARCHAR(30) NOT NULL,

    host VARCHAR(255) NOT NULL,

    port INTEGER NOT NULL,

    paper_width_mm INTEGER NOT NULL DEFAULT 80,

    auto_cut BOOLEAN NOT NULL DEFAULT TRUE,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_printer_device_branch
        FOREIGN KEY (branch_id)
        REFERENCES branches(id),

    CONSTRAINT uq_printer_device_branch_code
        UNIQUE (branch_id, code),

    CONSTRAINT ck_printer_device_port
        CHECK (port > 0 AND port <= 65535),

    CONSTRAINT ck_printer_device_paper_width
        CHECK (paper_width_mm > 0)
);


CREATE INDEX idx_printer_device_branch_station
    ON printer_devices (
        branch_id,
        station,
        active
    );


CREATE TABLE print_jobs (
    id BIGSERIAL PRIMARY KEY,

    branch_id BIGINT NOT NULL,

    printer_id BIGINT,

    kot_id BIGINT NOT NULL,

    job_type VARCHAR(40) NOT NULL,

    purpose VARCHAR(40) NOT NULL,

    station VARCHAR(50) NOT NULL,

    status VARCHAR(30) NOT NULL,

    copies INTEGER NOT NULL DEFAULT 1,

    attempt_count INTEGER NOT NULL DEFAULT 0,

    max_attempts INTEGER NOT NULL DEFAULT 5,

    claim_token VARCHAR(100),

    claimed_by_agent VARCHAR(120),

    claimed_at TIMESTAMP,

    claim_expires_at TIMESTAMP,

    queued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    first_attempt_at TIMESTAMP,

    last_attempt_at TIMESTAMP,

    printed_at TIMESTAMP,

    failed_at TIMESTAMP,

    next_attempt_at TIMESTAMP,

    last_error_code VARCHAR(100),

    last_error_message VARCHAR(500),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_print_job_branch
        FOREIGN KEY (branch_id)
        REFERENCES branches(id),

    CONSTRAINT fk_print_job_printer
        FOREIGN KEY (printer_id)
        REFERENCES printer_devices(id),

    CONSTRAINT fk_print_job_kot
        FOREIGN KEY (kot_id)
        REFERENCES kot(id),

    CONSTRAINT ck_print_job_copies
        CHECK (copies > 0),

    CONSTRAINT ck_print_job_attempt_count
        CHECK (attempt_count >= 0),

    CONSTRAINT ck_print_job_max_attempts
        CHECK (max_attempts > 0)
);


CREATE INDEX idx_print_job_queue
    ON print_jobs (
        branch_id,
        station,
        status,
        next_attempt_at,
        queued_at
    );


CREATE INDEX idx_print_job_printer_status
    ON print_jobs (
        printer_id,
        status,
        queued_at
    );


CREATE INDEX idx_print_job_kot
    ON print_jobs (
        kot_id
    );


CREATE INDEX idx_print_job_claim_expiry
    ON print_jobs (
        status,
        claim_expires_at
    );


/*
 * One KOT gets only one automatic INITIAL_KOT job.
 *
 * Manual reprints are intentionally allowed to create
 * multiple separate REPRINT jobs.
 */
CREATE UNIQUE INDEX uq_print_job_initial_kot
    ON print_jobs (
        kot_id
    )
    WHERE purpose = 'INITIAL_KOT';