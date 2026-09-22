CREATE TABLE customer_contacts (
    id BIGSERIAL PRIMARY KEY,

    normalized_phone VARCHAR(20) NOT NULL UNIQUE,

    latest_name VARCHAR(150),

    verification_status VARCHAR(30) NOT NULL DEFAULT 'UNVERIFIED',

    first_seen_at TIMESTAMP NOT NULL,

    last_seen_at TIMESTAMP NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_customer_contacts_verification_status
        CHECK (
            verification_status IN (
                'UNVERIFIED',
                'VERIFIED',
                'MERGED'
            )
        )
);


CREATE INDEX idx_customer_contacts_last_seen_at
    ON customer_contacts (
        last_seen_at DESC
    );


ALTER TABLE orders
    ADD COLUMN customer_phone_normalized VARCHAR(20);


ALTER TABLE orders
    ADD COLUMN customer_contact_id BIGINT
        REFERENCES customer_contacts(id);


CREATE INDEX idx_orders_customer_phone_normalized
    ON orders (
        customer_phone_normalized
    );


CREATE INDEX idx_orders_customer_contact_id
    ON orders (
        customer_contact_id
    );


/*
 * ---------------------------------------------------------
 * BACKFILL EXISTING ORDER PHONE SNAPSHOTS
 * ---------------------------------------------------------
 *
 * Supported historical input forms:
 *
 * 9876543210
 * 09876543210
 * 919876543210
 * +91 98765 43210
 *
 * Only valid-looking Indian mobile numbers beginning with
 * 6 / 7 / 8 / 9 are grouped.
 *
 * Anything else remains an order snapshot only and is NOT
 * treated as a customer identity.
 */

WITH cleaned AS (
    SELECT
        id,
        REGEXP_REPLACE(
            COALESCE(customer_phone, ''),
            '[^0-9]',
            '',
            'g'
        ) AS digits
    FROM orders
),
normalized AS (
    SELECT
        id,
        CASE
            WHEN LENGTH(digits) = 10
                 AND LEFT(digits, 1) IN ('6', '7', '8', '9')
                THEN '+91' || digits

            WHEN LENGTH(digits) = 11
                 AND LEFT(digits, 1) = '0'
                 AND SUBSTRING(digits FROM 2 FOR 1) IN ('6', '7', '8', '9')
                THEN '+91' || SUBSTRING(digits FROM 2)

            WHEN LENGTH(digits) = 12
                 AND LEFT(digits, 2) = '91'
                 AND SUBSTRING(digits FROM 3 FOR 1) IN ('6', '7', '8', '9')
                THEN '+' || digits

            ELSE NULL
        END AS normalized_phone
    FROM cleaned
)
UPDATE orders o
SET customer_phone_normalized =
        n.normalized_phone
FROM normalized n
WHERE o.id = n.id;


WITH normalized_orders AS (
    SELECT
        o.id,
        o.customer_phone_normalized AS normalized_phone,
        NULLIF(TRIM(o.customer_name), '') AS customer_name,
        o.created_at,
        MIN(o.created_at) OVER (
            PARTITION BY o.customer_phone_normalized
        ) AS first_seen_at,
        MAX(o.created_at) OVER (
            PARTITION BY o.customer_phone_normalized
        ) AS last_seen_at
    FROM orders o
    WHERE o.customer_phone_normalized IS NOT NULL
),
latest_per_phone AS (
    SELECT DISTINCT ON (normalized_phone)
        normalized_phone,
        customer_name,
        first_seen_at,
        last_seen_at
    FROM normalized_orders
    ORDER BY
        normalized_phone,
        created_at DESC,
        id DESC
)
INSERT INTO customer_contacts (
    normalized_phone,
    latest_name,
    verification_status,
    first_seen_at,
    last_seen_at,
    created_at,
    updated_at
)
SELECT
    normalized_phone,
    customer_name,
    'UNVERIFIED',
    first_seen_at,
    last_seen_at,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM latest_per_phone
ON CONFLICT (normalized_phone)
DO NOTHING;


UPDATE orders o
SET customer_contact_id =
        cc.id
FROM customer_contacts cc
WHERE o.customer_phone_normalized =
        cc.normalized_phone
  AND o.customer_contact_id IS NULL;
