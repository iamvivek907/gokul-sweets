CREATE TABLE order_pickup_codes (
 order_id BIGINT PRIMARY KEY REFERENCES orders(id),
 code VARCHAR(4) NOT NULL CHECK(code ~ '^[0-9]{4}$'),
 failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK(failed_attempts BETWEEN 0 AND 5),
 locked_until TIMESTAMPTZ,
 consumed_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE branches ADD COLUMN online_payment_fee_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN online_payment_fee_rate NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK(online_payment_fee_rate BETWEEN 0 AND 10),
 ADD COLUMN online_payment_fee_tax_rate NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK(online_payment_fee_tax_rate BETWEEN 0 AND 28);
ALTER TABLE orders ADD COLUMN payment_fee NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK(payment_fee>=0),
 ADD COLUMN payment_fee_tax NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK(payment_fee_tax BETWEEN 0 AND payment_fee),
 ADD COLUMN payment_fee_rate NUMERIC(5,2) NOT NULL DEFAULT 0,
 ADD COLUMN payment_fee_tax_rate NUMERIC(5,2) NOT NULL DEFAULT 0;
