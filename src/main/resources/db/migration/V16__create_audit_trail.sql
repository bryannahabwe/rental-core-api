-- Immutable audit trail: one row per significant action, storing a pre-built
-- human-readable sentence. Account-scoped; no foreign keys so a row survives
-- deletion of whatever it references.

CREATE TABLE audit_trail
(
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    account_id         UUID          NOT NULL,
    property_id        UUID,
    module             VARCHAR(32)   NOT NULL,
    action             VARCHAR(32)   NOT NULL,
    acting_user_id     UUID,
    acting_user_name   VARCHAR(255)  NOT NULL,
    affected_record_id VARCHAR(255),
    statement          VARCHAR(2000) NOT NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_audit_trail PRIMARY KEY (id)
);

CREATE INDEX idx_audit_trail_account_time ON audit_trail (account_id, created_at DESC);
CREATE INDEX idx_audit_trail_module ON audit_trail (module);
CREATE INDEX idx_audit_trail_action ON audit_trail (action);

-- Immutability: block any UPDATE/DELETE so the trail can't be tampered with.
CREATE OR REPLACE FUNCTION audit_trail_immutable()
    RETURNS TRIGGER AS
$$
BEGIN
    RAISE EXCEPTION 'audit_trail rows are immutable and cannot be % ', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_trail_immutable
    BEFORE UPDATE OR DELETE
    ON audit_trail
    FOR EACH ROW
EXECUTE FUNCTION audit_trail_immutable();
