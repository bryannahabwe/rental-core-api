-- Allow ADMIN to be assigned at the property level. ADMIN is now a
-- property-scoped role (an admin manages the properties they're assigned to),
-- so it must be permitted in the user_properties role check that previously
-- allowed only PROPERTY_MANAGER and CARETAKER.
ALTER TABLE user_properties DROP CONSTRAINT chk_user_properties_role;
ALTER TABLE user_properties ADD CONSTRAINT chk_user_properties_role
    CHECK (role IN ('ADMIN', 'PROPERTY_MANAGER', 'CARETAKER'));
