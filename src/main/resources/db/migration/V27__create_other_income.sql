CREATE TABLE other_income
(
    id           UUID           NOT NULL DEFAULT gen_random_uuid(),
    landlord_id  UUID           NOT NULL,
    property_id  UUID           NOT NULL,
    tenant_id    UUID,
    agreement_id UUID,
    income_date  DATE           NOT NULL,
    amount       DECIMAL(12, 2) NOT NULL,
    category     VARCHAR(255)   NOT NULL,
    method       VARCHAR(50)    NOT NULL DEFAULT 'CASH',
    reference    VARCHAR(255),
    notes        TEXT,
    created_at   TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_other_income PRIMARY KEY (id),
    CONSTRAINT fk_other_income_landlord FOREIGN KEY (landlord_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_other_income_property FOREIGN KEY (property_id) REFERENCES properties (id) ON DELETE RESTRICT,
    CONSTRAINT fk_other_income_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE SET NULL,
    CONSTRAINT fk_other_income_agreement FOREIGN KEY (agreement_id) REFERENCES rental_agreements (id) ON DELETE SET NULL,
    CONSTRAINT chk_other_income_method CHECK (method IN ('CASH', 'MOBILE_MONEY', 'BANK_TRANSFER', 'CHEQUE'))
);

CREATE INDEX idx_other_income_property ON other_income (property_id);

-- Backfill: existing settled forfeited deposits become first-class income rows.
-- Reports now sum other_income for non-rent income (see ReportService.getSummary),
-- so without this the historical forfeitures would drop out of revenue. Dated by
-- move_out_date, linked back to their agreement/tenant/property.
INSERT INTO other_income (id, landlord_id, property_id, tenant_id, agreement_id,
                          income_date, amount, category, method, reference, created_at)
SELECT gen_random_uuid(),
       a.landlord_id,
       a.property_id,
       a.tenant_id,
       a.id,
       COALESCE(a.move_out_date, CURRENT_DATE),
       a.deposit_forfeited,
       'Deposit forfeiture',
       'CASH',
       'Forfeited deposit at move-out',
       NOW()
FROM rental_agreements a
WHERE a.deposit_forfeited IS NOT NULL
  AND a.deposit_forfeited > 0;
