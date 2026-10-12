-- Minimal pre-V129 schema for exercising the real migration in an isolated schema.
CREATE TABLE verified_customer_subjects(id UUID PRIMARY KEY,environment VARCHAR(8),verified_phone VARCHAR(16),UNIQUE(environment,id));
CREATE TABLE verified_subject_rotations(environment VARCHAR(8),prior_subject_id UUID,new_subject_id UUID);
CREATE TABLE verified_order_ownership(order_id BIGINT PRIMARY KEY,environment VARCHAR(8),verified_subject_id UUID);
CREATE TABLE verified_order_transfer_audit(order_id BIGINT,environment VARCHAR(8),prior_subject_id UUID,new_subject_id UUID,transferred_at TIMESTAMPTZ,UNIQUE(environment,order_id,new_subject_id));
CREATE TABLE loyalty_accounts(environment VARCHAR(8),subject_id UUID,last_qualifying_activity TIMESTAMPTZ,PRIMARY KEY(environment,subject_id));
CREATE TABLE loyalty_ledger(id BIGINT PRIMARY KEY,environment VARCHAR(8),subject_id UUID,event_key VARCHAR(160),kind VARCHAR(30),coins INTEGER,order_id BIGINT,source_credit_id BIGINT,reason TEXT,expires_at TIMESTAMPTZ,created_at TIMESTAMPTZ,expiry_days INTEGER,badge_bonus_coins INTEGER,badge_name VARCHAR(80),UNIQUE(environment,subject_id,event_key));
CREATE TABLE loyalty_qualifying_orders(order_id BIGINT PRIMARY KEY,environment VARCHAR(8),subject_id UUID,eligible_subtotal NUMERIC,completed_at TIMESTAMPTZ,reversed BOOLEAN);
CREATE TABLE loyalty_lots(ledger_id BIGINT PRIMARY KEY,remaining INTEGER);
CREATE TABLE loyalty_holds(order_id BIGINT PRIMARY KEY,subject_id UUID,coins INTEGER);
INSERT INTO verified_customer_subjects VALUES ('00000000-0000-0000-0000-000000000003','DEV','+919876543210'),('00000000-0000-0000-0000-000000000004','PROD','+919876543210');
INSERT INTO verified_subject_rotations VALUES ('DEV','00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002'),('DEV','00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000003');
INSERT INTO verified_order_ownership VALUES (1,'DEV','00000000-0000-0000-0000-000000000001'),(2,'DEV','00000000-0000-0000-0000-000000000002'),(3,'PROD','00000000-0000-0000-0000-000000000004');
INSERT INTO loyalty_accounts VALUES ('DEV','00000000-0000-0000-0000-000000000001','2026-01-01'),('DEV','00000000-0000-0000-0000-000000000002','2026-02-01');
INSERT INTO loyalty_ledger VALUES (1,'DEV','00000000-0000-0000-0000-000000000001','welcome','WELCOME',30,1,NULL,'Original bonus','2026-07-01','2026-01-01',180,0,NULL),(2,'DEV','00000000-0000-0000-0000-000000000002','welcome','WELCOME',30,2,NULL,'Original bonus','2026-08-01','2026-02-01',180,0,NULL),(3,'DEV','00000000-0000-0000-0000-000000000001','reserve:1','RESERVED',-10,1,NULL,'Original reservation',NULL,'2026-01-02',180,0,NULL);
INSERT INTO loyalty_qualifying_orders VALUES (1,'DEV','00000000-0000-0000-0000-000000000001',300,'2026-01-01',FALSE),(2,'DEV','00000000-0000-0000-0000-000000000002',300,'2026-02-01',FALSE);
INSERT INTO loyalty_lots VALUES (1,20),(2,30);
INSERT INTO loyalty_holds VALUES (1,'00000000-0000-0000-0000-000000000001',10);
