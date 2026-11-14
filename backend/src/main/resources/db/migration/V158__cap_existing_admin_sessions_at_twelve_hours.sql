-- Match the new session limit for existing admin accounts, including push subscriptions
-- that use the stored expiry without a browser request.
UPDATE staff_sessions AS existing_session
SET expires_at = existing_session.created_at + INTERVAL '12 hours'
FROM staff_users AS staff
JOIN roles AS staff_role ON staff_role.id = staff.role_id
WHERE existing_session.staff_id = staff.id
  AND UPPER(staff_role.name) LIKE '%ADMIN%'
  AND existing_session.revoked_at IS NULL
  AND existing_session.expires_at > CURRENT_TIMESTAMP
  AND existing_session.expires_at > existing_session.created_at + INTERVAL '12 hours';
