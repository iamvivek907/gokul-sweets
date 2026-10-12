-- Recover only identities linked by server-verified PHONE_REVERIFICATION audits.
-- Never infer ownership from a typed order/customer-contact phone.
CREATE TABLE verified_customer_account_aliases (
 environment VARCHAR(8) NOT NULL CHECK(environment IN ('DEV','PROD')),
 prior_subject_id UUID NOT NULL,
 account_subject_id UUID NOT NULL,
 recovered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(environment,prior_subject_id),
 FOREIGN KEY(environment,account_subject_id)
   REFERENCES verified_customer_subjects(environment,id),
 CHECK(prior_subject_id<>account_subject_id)
);
CREATE INDEX verified_account_alias_target
 ON verified_customer_account_aliases(environment,account_subject_id);
WITH RECURSIVE chain(environment,account_subject_id,prior_subject_id) AS (
 SELECT s.environment,s.id,r.prior_subject_id
 FROM verified_customer_subjects s JOIN verified_subject_rotations r
 ON r.environment=s.environment AND r.new_subject_id=s.id
 UNION
 SELECT c.environment,c.account_subject_id,r.prior_subject_id
 FROM chain c JOIN verified_subject_rotations r
 ON r.environment=c.environment AND r.new_subject_id=c.prior_subject_id
)
INSERT INTO verified_customer_account_aliases(environment,prior_subject_id,account_subject_id)
SELECT DISTINCT environment,prior_subject_id,account_subject_id FROM chain;

INSERT INTO verified_order_transfer_audit
 (order_id,environment,prior_subject_id,new_subject_id,transferred_at)
SELECT o.order_id,o.environment,o.verified_subject_id,a.account_subject_id,CURRENT_TIMESTAMP
FROM verified_order_ownership o JOIN verified_customer_account_aliases a
 ON a.environment=o.environment AND a.prior_subject_id=o.verified_subject_id
ON CONFLICT(environment,order_id,new_subject_id) DO NOTHING;
UPDATE verified_order_ownership o SET verified_subject_id=a.account_subject_id
FROM verified_customer_account_aliases a
WHERE a.environment=o.environment AND a.prior_subject_id=o.verified_subject_id;

-- Financial ledger, lot balances, hold allocations and expiry dates are untouched.
-- The read projection exposes canonical ownership while retaining original expiry activity.
INSERT INTO loyalty_accounts(environment,subject_id)
SELECT DISTINCT a.environment,a.account_subject_id
FROM verified_customer_account_aliases a JOIN loyalty_accounts l
 ON l.environment=a.environment AND l.subject_id=a.prior_subject_id
ON CONFLICT DO NOTHING;
CREATE VIEW loyalty_account_ledger AS
SELECT l.id,l.environment,COALESCE(a.account_subject_id,l.subject_id) AS subject_id,
 l.event_key,l.kind,l.coins,l.order_id,l.source_credit_id,l.reason,l.expires_at,l.created_at,
 l.expiry_days,l.badge_bonus_coins,l.badge_name,l.subject_id AS original_subject_id
FROM loyalty_ledger l LEFT JOIN verified_customer_account_aliases a
 ON a.environment=l.environment AND a.prior_subject_id=l.subject_id;
CREATE VIEW loyalty_account_qualifying_orders AS
SELECT q.order_id,q.environment,COALESCE(a.account_subject_id,q.subject_id) AS subject_id,
 q.eligible_subtotal,q.completed_at,q.reversed
FROM loyalty_qualifying_orders q LEFT JOIN verified_customer_account_aliases a
 ON a.environment=q.environment AND a.prior_subject_id=q.subject_id;
-- Historical sessions remain revoked; old consent/address/push records are not reassigned.
COMMENT ON TABLE verified_customer_subjects IS
 'Stable account for a provider-verified phone in one environment; sessions are device-specific.';
