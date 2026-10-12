-- Optional customer-entered name; access is restricted to the verified session subject.
ALTER TABLE verified_customer_subjects ADD COLUMN display_name VARCHAR(80);
