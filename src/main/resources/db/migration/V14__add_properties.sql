-- Multi-property support: introduce a Property layer between the landlord and
-- their units/tenants/agreements/payments. Existing landlords are given a single
-- default property and all their existing rows are assigned to it, so nothing
-- breaks for current data.

-- 1. Properties table -------------------------------------------------------
CREATE TABLE properties
(
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    landlord_id UUID         NOT NULL,
    name        VARCHAR(255) NOT NULL,
    address     VARCHAR(255),
    description TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_properties PRIMARY KEY (id),
    CONSTRAINT fk_properties_landlord FOREIGN KEY (landlord_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_properties_landlord ON properties (landlord_id);

-- 2. One default property per existing landlord -----------------------------
-- Named after their business (landlord_settings.company_name) when set,
-- otherwise a generic placeholder they can rename.
INSERT INTO properties (landlord_id, name)
SELECT u.id,
       COALESCE(NULLIF(TRIM(ls.company_name), ''), 'My Property')
FROM users u
         LEFT JOIN landlord_settings ls ON ls.landlord_id = u.id;

-- 3. Add nullable property_id to the owned tables ---------------------------
ALTER TABLE rental_units ADD COLUMN property_id UUID;
ALTER TABLE tenants ADD COLUMN property_id UUID;
ALTER TABLE rental_agreements ADD COLUMN property_id UUID;
ALTER TABLE payments ADD COLUMN property_id UUID;

-- 4. Backfill from each landlord's (single) default property ----------------
UPDATE rental_units t
SET property_id = p.id
FROM properties p
WHERE p.landlord_id = t.landlord_id;

UPDATE tenants t
SET property_id = p.id
FROM properties p
WHERE p.landlord_id = t.landlord_id;

UPDATE rental_agreements t
SET property_id = p.id
FROM properties p
WHERE p.landlord_id = t.landlord_id;

UPDATE payments t
SET property_id = p.id
FROM properties p
WHERE p.landlord_id = t.landlord_id;

-- 5. Enforce NOT NULL + FK (RESTRICT: a property can't be deleted while it
--    still owns rows) + index each foreign key ----------------------------
ALTER TABLE rental_units ALTER COLUMN property_id SET NOT NULL;
ALTER TABLE rental_units
    ADD CONSTRAINT fk_rental_units_property FOREIGN KEY (property_id) REFERENCES properties (id) ON DELETE RESTRICT;
CREATE INDEX idx_rental_units_property ON rental_units (property_id);

ALTER TABLE tenants ALTER COLUMN property_id SET NOT NULL;
ALTER TABLE tenants
    ADD CONSTRAINT fk_tenants_property FOREIGN KEY (property_id) REFERENCES properties (id) ON DELETE RESTRICT;
CREATE INDEX idx_tenants_property ON tenants (property_id);

ALTER TABLE rental_agreements ALTER COLUMN property_id SET NOT NULL;
ALTER TABLE rental_agreements
    ADD CONSTRAINT fk_rental_agreements_property FOREIGN KEY (property_id) REFERENCES properties (id) ON DELETE RESTRICT;
CREATE INDEX idx_rental_agreements_property ON rental_agreements (property_id);

ALTER TABLE payments ALTER COLUMN property_id SET NOT NULL;
ALTER TABLE payments
    ADD CONSTRAINT fk_payments_property FOREIGN KEY (property_id) REFERENCES properties (id) ON DELETE RESTRICT;
CREATE INDEX idx_payments_property ON payments (property_id);
