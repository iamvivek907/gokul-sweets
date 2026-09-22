ALTER TABLE kot
    ADD COLUMN first_printed_by_staff_id BIGINT NULL,
    ADD COLUMN first_printed_by_staff_name VARCHAR(150) NULL,
    ADD COLUMN last_printed_by_staff_id BIGINT NULL,
    ADD COLUMN last_printed_by_staff_name VARCHAR(150) NULL;


CREATE INDEX idx_kot_first_printed_by_staff_id
    ON kot(first_printed_by_staff_id);


CREATE INDEX idx_kot_last_printed_by_staff_id
    ON kot(last_printed_by_staff_id);