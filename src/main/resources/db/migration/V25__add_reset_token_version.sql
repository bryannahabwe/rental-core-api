-- Single-use password-reset link support. Rotated when a reset is requested and
-- cleared once the password is reset, so only the newest link is ever valid.
-- Nullable: users that never request a reset simply have no version.
--
-- Numbered V25 (after V24) rather than filling the V23 gap: environments that
-- already booted the deposit/admin/rollover migrations have V24 applied, and
-- Flyway rejects a newly-added lower version (out-of-order) by default. Placing
-- it last applies cleanly on both already-migrated and fresh databases.
ALTER TABLE users ADD COLUMN reset_token_version UUID;
