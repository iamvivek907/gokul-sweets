-- Preserve only sessions that were valid under the preceding credential version model.
UPDATE staff_sessions s
SET revoked_at = CURRENT_TIMESTAMP
FROM staff_users u
WHERE u.id = s.staff_id AND s.revoked_at IS NULL
  AND (NOT u.active OR s.staff_updated_at IS DISTINCT FROM u.updated_at);

ALTER TABLE staff_sessions ADD COLUMN cookie_refreshed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE staff_sessions
SET expires_at = TIMESTAMPTZ 'infinity', cookie_refreshed_at = CURRENT_TIMESTAMP - INTERVAL '2 days'
WHERE revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP;

-- Staff details and branch access are read live. Credentials and deactivation revoke sessions.
CREATE FUNCTION revoke_staff_sessions_on_credentials_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.password_hash IS DISTINCT FROM OLD.password_hash
       OR NEW.username IS DISTINCT FROM OLD.username
       OR (OLD.active AND NOT NEW.active) THEN
        UPDATE staff_sessions SET revoked_at = CURRENT_TIMESTAMP
        WHERE staff_id = OLD.id AND revoked_at IS NULL;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER staff_credentials_changed
AFTER UPDATE OF password_hash, username, active ON staff_users
FOR EACH ROW EXECUTE FUNCTION revoke_staff_sessions_on_credentials_change();
