INSERT INTO permissions (
    name,
    description
)
VALUES
    (
        'REBATE_VIEW',
        'View rebate configuration'
    ),
    (
        'REBATE_MANAGE',
        'Create and manage rebates'
    )
ON CONFLICT (name)
DO NOTHING;


INSERT INTO role_permissions (
    role_id,
    permission_id
)
SELECT
    r.id,
    p.id
FROM roles r
JOIN permissions p
    ON p.name IN (
        'REBATE_VIEW',
        'REBATE_MANAGE'
    )
WHERE r.name = 'OWNER_ADMIN'
ON CONFLICT DO NOTHING;


INSERT INTO role_permissions (
    role_id,
    permission_id
)
SELECT
    r.id,
    p.id
FROM roles r
JOIN permissions p
    ON p.name IN (
        'REBATE_VIEW',
        'REBATE_MANAGE'
    )
WHERE r.name = 'MANAGER'
ON CONFLICT DO NOTHING;