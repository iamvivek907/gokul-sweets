CREATE TABLE approval_requests (
    id BIGSERIAL PRIMARY KEY,

    request_number VARCHAR(30) UNIQUE,

    request_type VARCHAR(30) NOT NULL,

    staff_user_id BIGINT NOT NULL
        REFERENCES staff_users(id),

    branch_id BIGINT NOT NULL
        REFERENCES branches(id),

    status VARCHAR(30) NOT NULL,

    title VARCHAR(180) NOT NULL,

    summary VARCHAR(1000),

    workflow_version INTEGER NOT NULL DEFAULT 1,

    submitted_at TIMESTAMP NOT NULL,

    resolved_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_approval_request_type
        CHECK (
            request_type IN (
                'LEAVE',
                'ATTENDANCE',
                'PAYMENT'
            )
        ),

    CONSTRAINT ck_approval_request_status
        CHECK (
            status IN (
                'PENDING',
                'APPROVED',
                'REJECTED',
                'SENT_BACK',
                'CANCELLED'
            )
        ),

    CONSTRAINT ck_approval_workflow_version
        CHECK (workflow_version >= 1)
);


CREATE INDEX idx_approval_requests_inbox
    ON approval_requests (
        branch_id,
        status,
        submitted_at DESC
    );


CREATE INDEX idx_approval_requests_staff
    ON approval_requests (
        staff_user_id,
        submitted_at DESC
    );


CREATE INDEX idx_approval_requests_type_status
    ON approval_requests (
        request_type,
        status,
        submitted_at DESC
    );


CREATE TABLE approval_request_history (
    id BIGSERIAL PRIMARY KEY,

    approval_request_id BIGINT NOT NULL
        REFERENCES approval_requests(id)
        ON DELETE CASCADE,

    action VARCHAR(30) NOT NULL,

    from_status VARCHAR(30),

    to_status VARCHAR(30) NOT NULL,

    actor_staff_user_id BIGINT NOT NULL
        REFERENCES staff_users(id),

    actor_name VARCHAR(150) NOT NULL,

    comment VARCHAR(1000),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_approval_history_action
        CHECK (
            action IN (
                'SUBMITTED',
                'APPROVED',
                'REJECTED',
                'SENT_BACK',
                'RESUBMITTED',
                'CANCELLED'
            )
        )
);


CREATE INDEX idx_approval_history_request
    ON approval_request_history (
        approval_request_id,
        created_at ASC,
        id ASC
    );


INSERT INTO permissions (
    name,
    description
)
VALUES
    (
        'APPROVAL_VIEW',
        'View staff approval requests within permitted branches.'
    ),
    (
        'APPROVAL_MANAGE',
        'Approve, reject, or send back staff approval requests within permitted branches.'
    )
ON CONFLICT (name)
DO NOTHING;


/*
 * Preserve the existing access model:
 *
 * - roles which already have STAFF_MANAGE receive the
 *   approval inbox permissions;
 * - OWNER_ADMIN also receives them explicitly.
 *
 * This matters because backend @PreAuthorize checks exact
 * authorities; OWNER_ADMIN is not an automatic backend
 * permission bypass.
 */

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
        'APPROVAL_VIEW',
        'APPROVAL_MANAGE'
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
