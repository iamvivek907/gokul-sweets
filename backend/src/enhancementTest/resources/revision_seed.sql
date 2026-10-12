-- Only catalogue/auth fixtures; policies, allocations and slots are configured through real admin APIs.
\set ON_ERROR_STOP on
DO $$ BEGIN
    IF current_database() <> 'gokul_enhancements_revision' THEN RAISE EXCEPTION 'Disposable revision DB required'; END IF;
END $$;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
INSERT INTO branches (id,code,name,active) VALUES (2001,'REVISION_ONE','Revision Branch',true),(2002,'REVISION_TWO','Other Branch',true);
INSERT INTO categories (id,code,name) VALUES (2001,'REV_SNACKS','Snacks'),(2002,'REV_SWEETS','Sweets');
INSERT INTO tax_categories (id,code,name,cgst_rate,sgst_rate,igst_rate,active,created_at,updated_at)
VALUES (2001,'REV_FIVE','Five percent',2.5,2.5,5,true,now(),now());
INSERT INTO products (id,code,category_id,name,base_price,tax_category_id,sale_mode,minimum_weight_grams,weight_step_grams) VALUES
(2001,'REV_PIECE',2001,'Revision Samosa',15,2001,'UNIT',null,null),
(2002,'REV_WEIGHT',2002,'Revision Kaju Katli',300,2001,'WEIGHT',250,50),
(2003,'REV_READY',2001,'Ready stock snack',20,2001,'UNIT',null,null),
(2004,'REV_DRAFT',2002,'Draft sweet',300,2001,'WEIGHT',250,50),
(2005,'REV_MANUAL',2001,'Manual snack',20,2001,'UNIT',null,null);
INSERT INTO branch_products (id,branch_id,product_id) VALUES (2001,2001,2001),(2002,2001,2002),(2003,2001,2003),(2004,2001,2004),(2005,2001,2005);
INSERT INTO staff_users (username,password_hash,full_name,role_id)
SELECT 'revision-owner',crypt('test-only',gen_salt('bf')),'Revision Owner',id FROM roles WHERE name='OWNER_ADMIN';
INSERT INTO roles (name,description) VALUES ('REVISION_CLERK','Synthetic restricted test role');
INSERT INTO role_permissions (role_id,permission_id)
SELECT r.id,p.id FROM roles r CROSS JOIN permissions p WHERE r.name='REVISION_CLERK' AND p.name IN ('ORDER_VIEW','INVENTORY_VIEW');
INSERT INTO staff_users (username,password_hash,full_name,role_id)
SELECT 'revision-clerk',crypt('test-only',gen_salt('bf')),'Revision Clerk',id FROM roles WHERE name='REVISION_CLERK';
INSERT INTO staff_branch_access (staff_user_id,branch_id) SELECT id,2001 FROM staff_users WHERE username='revision-clerk';
INSERT INTO roles (name,description) VALUES ('REVISION_OFFERS','Synthetic branch offer manager');
INSERT INTO role_permissions (role_id,permission_id)
SELECT r.id,p.id FROM roles r CROSS JOIN permissions p WHERE r.name='REVISION_OFFERS' AND p.name IN ('REBATE_VIEW','REBATE_MANAGE');
INSERT INTO staff_users (username,password_hash,full_name,role_id)
SELECT 'revision-offers',crypt('test-only',gen_salt('bf')),'Revision Offers',id FROM roles WHERE name='REVISION_OFFERS';
INSERT INTO staff_branch_access (staff_user_id,branch_id) SELECT id,2001 FROM staff_users WHERE username='revision-offers';
