CREATE TABLE branches (
    id BIGSERIAL PRIMARY KEY,

    code VARCHAR(50) NOT NULL UNIQUE,

    name VARCHAR(150) NOT NULL,

    address VARCHAR(500),

    city VARCHAR(100),

    state VARCHAR(100),

    pincode VARCHAR(10),

    phone VARCHAR(20),

    latitude DECIMAL(10, 7),

    longitude DECIMAL(10, 7),

    opening_time TIME,

    closing_time TIME,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);