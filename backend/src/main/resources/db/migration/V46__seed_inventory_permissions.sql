INSERT INTO permissions (name, description)
VALUES
    ('INVENTORY_VIEW', 'View branch inventory and daily online allocations'),
    ('INVENTORY_MANAGE', 'Configure and manage branch inventory allocations')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
    ON p.name IN ('INVENTORY_VIEW', 'INVENTORY_MANAGE')
WHERE r.name IN ('OWNER_ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;
