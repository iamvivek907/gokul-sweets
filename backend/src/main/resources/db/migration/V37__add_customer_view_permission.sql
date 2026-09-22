INSERT INTO permissions (
    name,
    description
)
VALUES
    (
        'CUSTOMER_VIEW',
        'View customer directory, customer purchase history, and customer-level business information.'
    )
ON CONFLICT (name)
DO NOTHING;


/*
 * Customer information is business/reporting data.
 *
 * Grant CUSTOMER_VIEW to:
 *
 * - OWNER_ADMIN explicitly, because backend @PreAuthorize
 *   checks exact authorities;
 *
 * - roles which already have REPORT_VIEW.
 *
 * This keeps customer data away from operational-only roles
 * such as kitchen/counter staff while allowing managers who
 * already have business reporting access to use the customer
 * directory.
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
    p.name = 'CUSTOMER_VIEW'
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
                AND existing_p.name = 'REPORT_VIEW'
        )
    )
ON CONFLICT DO NOTHING;
