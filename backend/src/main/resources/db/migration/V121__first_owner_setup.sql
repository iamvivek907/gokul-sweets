-- A single latch follows the staff directory (staff users are shared, not environment-scoped).
CREATE TABLE staff_owner_setup (
 singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK(singleton),
 completed_at TIMESTAMPTZ,
 first_owner_id BIGINT REFERENCES staff_users(id) ON DELETE SET NULL
);
INSERT INTO staff_owner_setup(singleton,completed_at)
SELECT TRUE,CASE WHEN EXISTS(SELECT 1 FROM staff_users u JOIN roles r ON r.id=u.role_id WHERE r.name='OWNER_ADMIN')
 THEN CURRENT_TIMESTAMP ELSE NULL END;
CREATE TABLE staff_owner_recovery (
 staff_id BIGINT PRIMARY KEY REFERENCES staff_users(id) ON DELETE CASCADE,
 key_hash CHAR(64) NOT NULL UNIQUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
