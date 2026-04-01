CREATE TABLE payments
(
    id           UUID           NOT NULL DEFAULT gen_random_uuid(),
    landlord_id  UUID           NOT NULL,
    tenant_id    UUID           NOT NULL,
    unit_id      UUID           NOT NULL,
    agreement_id UUID           NOT NULL,
    payment_date DATE           NOT NULL,
    amount       DECIMAL(12, 2) NOT NULL,
    method       VARCHAR(50)    NOT NULL DEFAULT 'CASH',
    reference    VARCHAR(255),
    notes        TEXT,
    created_at   TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT fk_payments_landlord FOREIGN KEY (landlord_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_payments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_unit FOREIGN KEY (unit_id) REFERENCES rental_units (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_agreement FOREIGN KEY (agreement_id) REFERENCES rental_agreements (id) ON DELETE RESTRICT,
    CONSTRAINT chk_payments_method CHECK (method IN ('CASH'))
);