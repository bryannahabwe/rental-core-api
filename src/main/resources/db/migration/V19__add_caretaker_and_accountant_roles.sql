-- Two new roles, and a role on each property assignment so one person can be a
-- Property Manager at one property and a Caretaker at another.
--
-- Existing rows keep their meaning exactly: every user_properties row today
-- belongs to a PROPERTY_MANAGER (replaceAssignments deletes the rows for every
-- other role), so the backfill is a constant.

-- 1. Widen the account-role check -------------------------------------------
ALTER TABLE users DROP CONSTRAINT chk_users_role;
ALTER TABLE users ADD CONSTRAINT chk_users_role
    CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'PROPERTY_MANAGER', 'CARETAKER', 'ACCOUNTANT'));

-- 2. The role held at each assigned property ---------------------------------
ALTER TABLE user_properties ADD COLUMN role VARCHAR(30);
UPDATE user_properties SET role = 'PROPERTY_MANAGER';
ALTER TABLE user_properties ALTER COLUMN role SET NOT NULL;

-- Only the property-scoped roles can appear here; the account-wide roles
-- (owner, admin, accountant) never carry assignments.
ALTER TABLE user_properties ADD CONSTRAINT chk_user_properties_role
    CHECK (role IN ('PROPERTY_MANAGER', 'CARETAKER'));

-- No new index: uq_user_properties (user_id, property_id) already serves the
-- (user, property) -> role lookup the effective-role resolution performs.
