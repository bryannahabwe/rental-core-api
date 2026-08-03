-- Platform (Cognix) staff, and their time-boxed read-only access to a customer
-- account.
--
-- Platform staff deliberately live OUTSIDE the customer model rather than as a
-- sixth UserRole: users.account_owner_id is NOT NULL with a self-FK, so a
-- platform admin placed in `users` would have to belong to some account and
-- would then appear in that customer's own user list. Keeping them separate
-- also keeps the customer role enum closed, so no path exists for an account
-- ADMIN to hand out platform access.

CREATE TABLE platform_users
(
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_platform_users PRIMARY KEY (id),
    CONSTRAINT uq_platform_users_email UNIQUE (email),
    CONSTRAINT chk_platform_users_status CHECK (status IN ('ACTIVE', 'DEACTIVATED'))
);

-- One support engagement against one customer account. Rows are never deleted:
-- ending a session sets ended_at, so the history stays answerable.
--
-- created_at is the session start (BaseEntity supplies it, so there is no
-- separate started_at to drift out of step with it).
CREATE TABLE support_sessions
(
    id               UUID      NOT NULL DEFAULT gen_random_uuid(),
    platform_user_id UUID      NOT NULL,
    account_id       UUID      NOT NULL,
    reason           TEXT      NOT NULL,
    expires_at       TIMESTAMP NOT NULL,
    ended_at         TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_support_sessions PRIMARY KEY (id),
    CONSTRAINT fk_support_sessions_platform_user
        FOREIGN KEY (platform_user_id) REFERENCES platform_users (id),
    -- account_id is the owner user's id, matching the landlord_id anchor every
    -- other table scopes by.
    CONSTRAINT fk_support_sessions_account
        FOREIGN KEY (account_id) REFERENCES users (id)
);

CREATE INDEX idx_support_sessions_account ON support_sessions (account_id);
CREATE INDEX idx_support_sessions_platform_user ON support_sessions (platform_user_id);

-- No seed. The first platform user is inserted by hand, deliberately: creating
-- one should never be a code path in this application.
