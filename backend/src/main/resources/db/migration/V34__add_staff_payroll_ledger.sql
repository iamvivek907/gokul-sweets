CREATE TABLE staff_compensation_profiles (
    id BIGSERIAL PRIMARY KEY,

    staff_user_id BIGINT NOT NULL
        REFERENCES staff_users(id),

    effective_from DATE NOT NULL,

    daily_rate DECIMAL(12,2) NOT NULL,

    half_day_rate DECIMAL(12,2) NOT NULL,

    created_by_staff_user_id BIGINT NOT NULL
        REFERENCES staff_users(id),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_staff_compensation_effective
        UNIQUE (
            staff_user_id,
            effective_from
        ),

    CONSTRAINT ck_staff_compensation_daily_rate
        CHECK (daily_rate >= 0),

    CONSTRAINT ck_staff_compensation_half_day_rate
        CHECK (half_day_rate >= 0)
);


CREATE INDEX idx_staff_compensation_lookup
    ON staff_compensation_profiles (
        staff_user_id,
        effective_from DESC
    );


CREATE TABLE staff_earnings_ledger (
    id BIGSERIAL PRIMARY KEY,

    staff_user_id BIGINT NOT NULL
        REFERENCES staff_users(id),

    branch_id BIGINT NOT NULL
        REFERENCES branches(id),

    attendance_id BIGINT NOT NULL UNIQUE
        REFERENCES staff_attendance(id),

    earning_date DATE NOT NULL,

    attendance_type VARCHAR(30) NOT NULL,

    rate_snapshot DECIMAL(12,2) NOT NULL,

    amount DECIMAL(12,2) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_staff_earnings_rate
        CHECK (rate_snapshot >= 0),

    CONSTRAINT ck_staff_earnings_amount
        CHECK (amount >= 0)
);


CREATE INDEX idx_staff_earnings_staff_date
    ON staff_earnings_ledger (
        staff_user_id,
        earning_date DESC
    );


CREATE INDEX idx_staff_earnings_branch_date
    ON staff_earnings_ledger (
        branch_id,
        earning_date DESC
    );


CREATE TABLE staff_payment_requests (
    id BIGSERIAL PRIMARY KEY,

    staff_user_id BIGINT NOT NULL
        REFERENCES staff_users(id),

    branch_id BIGINT NOT NULL
        REFERENCES branches(id),

    approval_request_id BIGINT NOT NULL UNIQUE
        REFERENCES approval_requests(id),

    amount DECIMAL(12,2) NOT NULL,

    note VARCHAR(1000),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_staff_payment_request_amount
        CHECK (amount > 0)
);


CREATE INDEX idx_staff_payment_requests_staff
    ON staff_payment_requests (
        staff_user_id,
        created_at DESC
    );


CREATE INDEX idx_staff_payment_requests_branch
    ON staff_payment_requests (
        branch_id,
        created_at DESC
    );


INSERT INTO permissions (
    name,
    description
)
VALUES
    (
        'PAYROLL_VIEW',
        'View staff payroll and earnings within permitted scope.'
    ),
    (
        'PAYROLL_MANAGE',
        'Manage staff compensation and payroll settings within permitted scope.'
    )
ON CONFLICT (name)
DO NOTHING;


INSERT INTO role_permissions (
    role_id,
    permission_id
)
SELECT DISTINCT
    r.id,
    p.id
FROM roles r
CROSS JOIN permissions p
WHERE
    p.name IN (
        'PAYROLL_VIEW',
        'PAYROLL_MANAGE'
    )
    AND
    (
        r.name = 'OWNER_ADMIN'
        OR EXISTS (
            SELECT 1
            FROM role_permissions existing_rp
            JOIN permissions existing_p
                ON existing_p.id = existing_rp.permission_id
            WHERE
                existing_rp.role_id = r.id
                AND existing_p.name = 'STAFF_MANAGE'
        )
    )
ON CONFLICT DO NOTHING;
