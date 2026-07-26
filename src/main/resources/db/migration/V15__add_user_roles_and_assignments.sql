-- User management & roles within a single account. Existing users all become
-- active SUPER_ADMIN owners anchoring their own account. New columns are added
-- with backfill-friendly defaults, then tightened.

-- 1. Role, status, and account anchor on users ------------------------------
ALTER TABLE users ADD COLUMN role VARCHAR(30) NOT NULL DEFAULT 'SUPER_ADMIN';
ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN account_owner_id UUID;

-- Every existing user is the owner of their own account.
UPDATE users SET account_owner_id = id;

ALTER TABLE users ALTER COLUMN account_owner_id SET NOT NULL;
ALTER TABLE users
    ADD CONSTRAINT fk_users_account_owner FOREIGN KEY (account_owner_id) REFERENCES users (id);
CREATE INDEX idx_users_account_owner ON users (account_owner_id);

-- Invited staff join by email and have no phone/password until they accept.
ALTER TABLE users ALTER COLUMN phone_number DROP NOT NULL;
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE users ADD CONSTRAINT chk_users_role
    CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'PROPERTY_MANAGER'));
ALTER TABLE users ADD CONSTRAINT chk_users_status
    CHECK (status IN ('ACTIVE', 'INVITED', 'DEACTIVATED'));

-- 2. Property-manager → property assignments --------------------------------
CREATE TABLE user_properties
(
    id          UUID      NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID      NOT NULL,
    property_id UUID      NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_user_properties PRIMARY KEY (id),
    CONSTRAINT uq_user_properties UNIQUE (user_id, property_id),
    CONSTRAINT fk_user_properties_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_properties_property FOREIGN KEY (property_id) REFERENCES properties (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_properties_user ON user_properties (user_id);
