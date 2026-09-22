ALTER TABLE rebates
    ADD COLUMN visibility VARCHAR(30);


UPDATE rebates
SET visibility = 'PUBLIC'
WHERE visibility IS NULL;


ALTER TABLE rebates
    ALTER COLUMN visibility SET DEFAULT 'PUBLIC',
    ALTER COLUMN visibility SET NOT NULL,

    ADD CONSTRAINT chk_rebate_visibility
        CHECK (
            visibility IN (
                'PUBLIC',
                'CODE_ONLY'
            )
        );


CREATE INDEX idx_rebates_visibility_active_validity
    ON rebates (
        visibility,
        active,
        valid_from,
        valid_until
    );
