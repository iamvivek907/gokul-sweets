CREATE TABLE staff_leave_requests (
    id BIGSERIAL PRIMARY KEY,

    staff_user_id BIGINT NOT NULL
        REFERENCES staff_users(id),

    branch_id BIGINT NOT NULL
        REFERENCES branches(id),

    approval_request_id BIGINT NOT NULL UNIQUE
        REFERENCES approval_requests(id),

    start_date DATE NOT NULL,

    end_date DATE NOT NULL,

    reason VARCHAR(1000) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_staff_leave_request_dates
        CHECK (end_date >= start_date)
);


CREATE INDEX idx_staff_leave_requests_staff_dates
    ON staff_leave_requests (
        staff_user_id,
        start_date DESC,
        end_date DESC
    );


CREATE INDEX idx_staff_leave_requests_branch_dates
    ON staff_leave_requests (
        branch_id,
        start_date DESC,
        end_date DESC
    );
