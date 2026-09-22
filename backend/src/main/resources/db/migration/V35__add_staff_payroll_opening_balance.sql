CREATE TABLE staff_payroll_opening_balances (
    id BIGSERIAL PRIMARY KEY,

    staff_user_id BIGINT NOT NULL UNIQUE
        REFERENCES staff_users(id),

    as_of_date DATE NOT NULL,

    earned_amount DECIMAL(12,2) NOT NULL,

    taken_amount DECIMAL(12,2) NOT NULL,

    note VARCHAR(1000),

    created_by_staff_user_id BIGINT NOT NULL
        REFERENCES staff_users(id),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_staff_payroll_opening_earned
        CHECK (earned_amount >= 0),

    CONSTRAINT ck_staff_payroll_opening_taken
        CHECK (taken_amount >= 0),

    CONSTRAINT ck_staff_payroll_opening_taken_not_above_earned
        CHECK (taken_amount <= earned_amount)
);


CREATE INDEX idx_staff_payroll_opening_staff
    ON staff_payroll_opening_balances (
        staff_user_id
    );
