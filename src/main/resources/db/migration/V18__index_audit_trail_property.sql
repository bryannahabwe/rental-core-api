-- The activity feed can now be scoped to the property selected in the header
-- (account-level rows, property_id IS NULL, are always included), so index the
-- account + property + time access path the feed query uses.

CREATE INDEX idx_audit_trail_account_property_time
    ON audit_trail (account_id, property_id, created_at DESC);
