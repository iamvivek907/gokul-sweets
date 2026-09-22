-- =========================================================
-- PERMISSIONS
-- =========================================================

INSERT INTO permissions (
    name,
    description
)
VALUES
    (
        'ORDER_VIEW',
        'View branch orders'
    ),
    (
        'ORDER_START_PREPARATION',
        'Move confirmed orders to preparing'
    ),
    (
        'ORDER_MARK_READY',
        'Mark prepared orders ready for pickup'
    ),
    (
        'ORDER_MARK_PICKED_UP',
        'Mark ready orders as picked up'
    ),
    (
        'ORDER_CANCEL',
        'Cancel eligible orders'
    ),
    (
        'DISCOUNT_APPLY',
        'Apply allowed discounts'
    ),
    (
        'DISCOUNT_OVERRIDE_LIMIT',
        'Override standard discount limits'
    ),
    (
        'REFUND_CREATE',
        'Create payment refund requests'
    ),
    (
        'REPORT_VIEW',
        'View business reports'
    ),
    (
        'MENU_MANAGE',
        'Manage menu and product availability'
    ),
    (
        'STAFF_MANAGE',
        'Manage staff users and access'
    ),
    (
        'BRANCH_MANAGE',
        'Manage branch configuration'
    )
ON CONFLICT (name)
DO NOTHING;


-- =========================================================
-- ROLES
-- =========================================================

INSERT INTO roles (
    name,
    description
)
VALUES
    (
        'OWNER_ADMIN',
        'Full business administration'
    ),
    (
        'MANAGER',
        'Branch management and operations'
    ),
    (
        'KITCHEN_STAFF',
        'Kitchen order preparation'
    ),
    (
        'COUNTER_STAFF',
        'Pickup and counter operations'
    ),
    (
        'VIEWER',
        'Read-only order access'
    )
ON CONFLICT (name)
DO NOTHING;


-- =========================================================
-- OWNER_ADMIN
-- Gets every permission
-- =========================================================

INSERT INTO role_permissions (
    role_id,
    permission_id
)
SELECT
    r.id,
    p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'OWNER_ADMIN'
ON CONFLICT DO NOTHING;


-- =========================================================
-- MANAGER
-- =========================================================

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
        'ORDER_VIEW',
        'ORDER_START_PREPARATION',
        'ORDER_MARK_READY',
        'ORDER_MARK_PICKED_UP',
        'ORDER_CANCEL',
        'DISCOUNT_APPLY',
        'REFUND_CREATE',
        'REPORT_VIEW',
        'MENU_MANAGE'
    )
WHERE r.name = 'MANAGER'
ON CONFLICT DO NOTHING;


-- =========================================================
-- KITCHEN_STAFF
-- =========================================================

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
        'ORDER_VIEW',
        'ORDER_START_PREPARATION',
        'ORDER_MARK_READY'
    )
WHERE r.name = 'KITCHEN_STAFF'
ON CONFLICT DO NOTHING;


-- =========================================================
-- COUNTER_STAFF
-- =========================================================

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
        'ORDER_VIEW',
        'ORDER_MARK_PICKED_UP'
    )
WHERE r.name = 'COUNTER_STAFF'
ON CONFLICT DO NOTHING;


-- =========================================================
-- VIEWER
-- =========================================================

INSERT INTO role_permissions (
    role_id,
    permission_id
)
SELECT
    r.id,
    p.id
FROM roles r
JOIN permissions p
    ON p.name = 'ORDER_VIEW'
WHERE r.name = 'VIEWER'
ON CONFLICT DO NOTHING;