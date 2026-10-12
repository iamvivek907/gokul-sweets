ALTER TABLE branches ADD COLUMN pickup_convenience_fee NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK(pickup_convenience_fee BETWEEN 0 AND 100), ADD COLUMN pickup_convenience_fee_tax_rate NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK(pickup_convenience_fee_tax_rate BETWEEN 0 AND 28);
ALTER TABLE orders ADD COLUMN convenience_fee NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK(convenience_fee >= 0), ADD COLUMN convenience_fee_tax NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK(convenience_fee_tax BETWEEN 0 AND convenience_fee);

ALTER TABLE branches ADD COLUMN pickup_fee_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN convenience_fee_tax_rate NUMERIC(5,2) NOT NULL DEFAULT 0;
