CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE permissions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,

    PRIMARY KEY (
        role_id,
        permission_id
    ),

    CONSTRAINT fk_role_permissions_role
        FOREIGN KEY (role_id)
        REFERENCES roles(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_role_permissions_permission
        FOREIGN KEY (permission_id)
        REFERENCES permissions(id)
        ON DELETE CASCADE
);

CREATE TABLE staff_users (
    id BIGSERIAL PRIMARY KEY,

    username VARCHAR(100) NOT NULL UNIQUE,

    password_hash VARCHAR(255) NOT NULL,

    full_name VARCHAR(150) NOT NULL,

    phone VARCHAR(20),

    active BOOLEAN NOT NULL DEFAULT TRUE,

    role_id BIGINT NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_staff_users_role
        FOREIGN KEY (role_id)
        REFERENCES roles(id)
);

CREATE TABLE staff_branch_access (
    staff_user_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,

    PRIMARY KEY (
        staff_user_id,
        branch_id
    ),

    CONSTRAINT fk_staff_branch_access_staff
        FOREIGN KEY (staff_user_id)
        REFERENCES staff_users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_staff_branch_access_branch
        FOREIGN KEY (branch_id)
        REFERENCES branches(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_staff_users_role
    ON staff_users(role_id);

CREATE INDEX idx_staff_branch_access_branch
    ON staff_branch_access(branch_id);