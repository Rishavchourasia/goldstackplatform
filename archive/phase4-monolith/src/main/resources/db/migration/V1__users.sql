
CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,   -- BCrypt hash, never the raw password
    full_name     VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX ux_users_email ON users (LOWER(email));
