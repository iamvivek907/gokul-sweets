-- Synthetic fixtures ONLY for a disposable local database. Never run against a business database.
\set ON_ERROR_STOP on
DO $$ BEGIN
    IF current_database() <> 'gokul_enhancements' THEN
        RAISE EXCEPTION 'This fixture requires the disposable gokul_enhancements database';
    END IF;
END $$;
SET TIME ZONE 'Asia/Kolkata';
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO branches (id, code, name, active) VALUES
    (1001, 'TEST_ONE', 'Test Branch One', true), (1002, 'TEST_TWO', 'Test Branch Two', true);
INSERT INTO categories (id, code, name) VALUES (1001, 'TEST_SWEETS', 'Test sweets');
INSERT INTO tax_categories (id, code, name, cgst_rate, sgst_rate, igst_rate, active, created_at, updated_at)
VALUES (1001, 'TEST_FIVE', 'Test five percent', 2.5, 2.5, 5, true, now(), now());
INSERT INTO products (id, code, category_id, name, base_price, tax_category_id, sale_mode, minimum_weight_grams, weight_step_grams)
VALUES
    (1001, 'TEST_PIECE', 1001, 'Test Rasgulla', 15, 1001, 'UNIT', null, null),
    (1002, 'TEST_WEIGHT', 1001, 'Test Gulab Jamun', 300, 1001, 'WEIGHT', 250, 50),
    (1003, 'TEST_LEGACY', 1001, 'Test legacy readiness', 300, 1001, 'WEIGHT', 250, 50),
    (1004, 'TEST_AUTO', 1001, 'Test automatic production', 300, 1001, 'WEIGHT', 250, 50);
INSERT INTO branch_products (id, branch_id, product_id) VALUES (1001,1001,1001),(1002,1001,1002),(1003,1001,1003),(1004,1001,1004);
INSERT INTO branch_pickup_settings (branch_id, advance_booking_days, opening_time, closing_time)
VALUES (1001,60,'09:00','21:00'), (1002,60,'09:00','21:00');
INSERT INTO pickup_slots (id, branch_id, slot_date, start_time, end_time, capacity)
VALUES
    (1001,1001,current_date+1,'09:00','09:30',20),
    (1002,1001,current_date+1,'10:30','11:00',20),
    (1003,1001,current_date+1,'12:00','12:30',20),
    (1004,1001,current_date+2,'12:00','12:30',20),
    (1005,1001,current_date+31,'12:00','12:30',20),
    (1006,1001,current_date+2,'13:00','13:30',1);
INSERT INTO branch_inventory_policies
    (branch_product_id, control_mode, inventory_unit, online_enabled, booking_horizon_days, production_lead_minutes,
     maximum_daily_allocation, default_safety_buffer, created_at, updated_at)
VALUES (1001,'DAILY_PRODUCTION','PIECE',true,60,0,100,0,now(),now()),
       (1002,'DAILY_PRODUCTION','GRAM',true,60,0,5000,0,now(),now()),
       (1003,'DAILY_PRODUCTION','GRAM',true,60,0,5000,0,now(),now()),
       (1004,'DAILY_PRODUCTION','GRAM',true,60,0,500,100,now(),now());
INSERT INTO inventory_daily_allocations
    (branch_product_id, service_date, status, inventory_unit, approved_quantity, expected_ready_at, created_at, updated_at)
VALUES
    (1001,current_date+1,'APPROVED','PIECE',10,null,now(),now()),
    (1001,current_date+2,'APPROVED','PIECE',100,null,now(),now()),
    (1001,current_date+31,'APPROVED','PIECE',10,null,now(),now()),
    (1002,current_date+1,'APPROVED','GRAM',500,(current_date+1)+time '10:30',now(),now()),
    (1002,current_date+2,'APPROVED','GRAM',1000,null,now(),now()),
    (1003,current_date+1,'APPROVED','GRAM',1000,(current_date+1)+time '20:00',now(),now());
INSERT INTO inventory_automation_rules
    (branch_product_id, automation_mode, guaranteed_quantity, forecast_enabled, generation_horizon_days, created_at, updated_at)
VALUES (1004,'AUTO_APPROVE_GUARANTEED',1000,false,60,now(),now());
INSERT INTO staff_users (username, password_hash, full_name, role_id)
SELECT 'test-owner', crypt('test-only', gen_salt('bf')), 'Synthetic test owner', id FROM roles WHERE name='OWNER_ADMIN';
INSERT INTO roles (name, description) VALUES ('TEST_BRANCH_MANAGER', 'Synthetic branch-scoped test role');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name='TEST_BRANCH_MANAGER' AND p.name IN ('BRANCH_MANAGE', 'MENU_MANAGE');
INSERT INTO staff_users (username, password_hash, full_name, role_id)
SELECT 'test-manager', crypt('test-only', gen_salt('bf')), 'Synthetic test manager', id FROM roles WHERE name='TEST_BRANCH_MANAGER';
INSERT INTO staff_branch_access (staff_user_id, branch_id)
SELECT id,1001 FROM staff_users WHERE username='test-manager';
INSERT INTO analytics_product_daily (business_date, branch_id, product_id, category_id, order_count)
VALUES (current_date,1001,1001,1001,10),(current_date,1001,1002,1001,20);
