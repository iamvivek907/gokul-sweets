-- Bind enrollment challenges to the account version used during sign-in.
-- Preserve existing challenges and accounts during migration. Old application
-- instances may still insert NULL snapshots; new validation fails closed for those.
ALTER TABLE staff_mfa_enrollments ADD COLUMN staff_updated_at TIMESTAMP;
UPDATE staff_mfa_enrollments e SET staff_updated_at=u.updated_at
FROM staff_users u WHERE u.id=e.staff_id;
