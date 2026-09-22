CREATE TABLE staff_attendance (
    id BIGSERIAL PRIMARY KEY,

    staff_user_id BIGINT NOT NULL
        REFERENCES staff_users(id),

    branch_id BIGINT NOT NULL
        REFERENCES branches(id),

    approval_request_id BIGINT NOT NULL UNIQUE
        REFERENCES approval_requests(id),

    attendance_date DATE NOT NULL,

    attendance_type VARCHAR(30) NOT NULL,

    check_in_time TIME,

    check_out_time TIME,

    note VARCHAR(1000),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_staff_attendance_type
        CHECK (
            attendance_type IN (
                'PRESENT',
                'HALF_DAY',
                'ABSENT'
            )
        ),

    CONSTRAINT ck_staff_attendance_times
        CHECK (
            check_in_time IS NULL
            OR check_out_time IS NULL
            OR check_out_time > check_in_time
        )
);


CREATE INDEX idx_staff_attendance_staff_date
    ON staff_attendance (
        staff_user_id,
        attendance_date DESC
    );


CREATE INDEX idx_staff_attendance_branch_date
    ON staff_attendance (
        branch_id,
        attendance_date DESC
    );
