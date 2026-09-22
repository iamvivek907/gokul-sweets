CREATE TABLE inventory_automation_rules (
    id BIGSERIAL PRIMARY KEY,
    branch_product_id BIGINT NOT NULL,
    automation_mode VARCHAR(40) NOT NULL,
    guaranteed_quantity NUMERIC(14, 3) NOT NULL DEFAULT 0,
    forecast_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    lookback_weeks INTEGER NOT NULL DEFAULT 8,
    minimum_history_days INTEGER NOT NULL DEFAULT 3,
    demand_multiplier NUMERIC(8, 3) NOT NULL DEFAULT 1.000,
    maximum_suggested_quantity NUMERIC(14, 3),
    available_days_mask SMALLINT NOT NULL DEFAULT 127,
    seasonal_mode VARCHAR(30) NOT NULL DEFAULT 'ALWAYS',
    generation_horizon_days INTEGER,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_automation_rule_branch_product
        FOREIGN KEY (branch_product_id) REFERENCES branch_products(id),
    CONSTRAINT uk_automation_rule_branch_product UNIQUE (branch_product_id),
    CONSTRAINT ck_automation_rule_mode CHECK (automation_mode IN (
        'SUGGEST_ONLY', 'CREATE_DRAFT', 'AUTO_APPROVE_GUARANTEED'
    )),
    CONSTRAINT ck_automation_rule_seasonal CHECK (seasonal_mode IN (
        'ALWAYS', 'WINDOW_ONLY', 'MANUAL_ONLY'
    )),
    CONSTRAINT ck_automation_rule_quantities CHECK (
        guaranteed_quantity >= 0
        AND (maximum_suggested_quantity IS NULL OR maximum_suggested_quantity > 0)
        AND demand_multiplier > 0
    ),
    CONSTRAINT ck_automation_rule_history CHECK (
        lookback_weeks BETWEEN 1 AND 52
        AND minimum_history_days BETWEEN 1 AND 52
    ),
    CONSTRAINT ck_automation_rule_days CHECK (available_days_mask BETWEEN 1 AND 127),
    CONSTRAINT ck_automation_rule_horizon CHECK (
        generation_horizon_days IS NULL OR generation_horizon_days BETWEEN 0 AND 365
    )
);

CREATE TABLE inventory_availability_windows (
    id BIGSERIAL PRIMARY KEY,
    automation_rule_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_availability_window_rule
        FOREIGN KEY (automation_rule_id) REFERENCES inventory_automation_rules(id) ON DELETE CASCADE,
    CONSTRAINT ck_availability_window_dates CHECK (end_date >= start_date),
    CONSTRAINT uk_availability_window_rule_dates UNIQUE (
        automation_rule_id, start_date, end_date
    )
);

CREATE TABLE inventory_automation_runs (
    id BIGSERIAL PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    from_date DATE NOT NULL,
    through_date DATE NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,
    run_status VARCHAR(20) NOT NULL,
    created_count INTEGER NOT NULL DEFAULT 0,
    updated_count INTEGER NOT NULL DEFAULT 0,
    suggested_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    initiated_by VARCHAR(150) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    error_summary VARCHAR(500),

    CONSTRAINT fk_automation_run_branch FOREIGN KEY (branch_id) REFERENCES branches(id),
    CONSTRAINT ck_automation_run_dates CHECK (through_date >= from_date),
    CONSTRAINT ck_automation_run_trigger CHECK (trigger_type IN ('MANUAL', 'SCHEDULED')),
    CONSTRAINT ck_automation_run_status CHECK (run_status IN ('RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED'))
);

CREATE TABLE inventory_automation_run_items (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL,
    branch_product_id BIGINT NOT NULL,
    service_date DATE NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    proposed_quantity NUMERIC(14, 3),
    forecast_quantity NUMERIC(14, 3),
    message VARCHAR(500),

    CONSTRAINT fk_automation_item_run
        FOREIGN KEY (run_id) REFERENCES inventory_automation_runs(id) ON DELETE CASCADE,
    CONSTRAINT fk_automation_item_branch_product
        FOREIGN KEY (branch_product_id) REFERENCES branch_products(id),
    CONSTRAINT ck_automation_item_outcome CHECK (outcome IN (
        'CREATED', 'UPDATED', 'SUGGESTED', 'SKIPPED', 'ERROR'
    ))
);

CREATE TABLE inventory_automation_managed_allocations (
    id BIGSERIAL PRIMARY KEY,
    allocation_id BIGINT NOT NULL,
    automation_rule_id BIGINT NOT NULL,
    last_run_id BIGINT NOT NULL,
    last_automated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_managed_allocation
        FOREIGN KEY (allocation_id) REFERENCES inventory_daily_allocations(id) ON DELETE CASCADE,
    CONSTRAINT fk_managed_allocation_rule
        FOREIGN KEY (automation_rule_id) REFERENCES inventory_automation_rules(id),
    CONSTRAINT fk_managed_allocation_run
        FOREIGN KEY (last_run_id) REFERENCES inventory_automation_runs(id),
    CONSTRAINT uk_managed_allocation UNIQUE (allocation_id)
);

CREATE INDEX idx_automation_rule_active ON inventory_automation_rules(active);
CREATE INDEX idx_automation_window_dates ON inventory_availability_windows(start_date, end_date);
CREATE INDEX idx_automation_run_branch_started ON inventory_automation_runs(branch_id, started_at DESC);
CREATE INDEX idx_automation_item_run ON inventory_automation_run_items(run_id);
