CREATE TABLE rental_agreements
(
    id             UUID           NOT NULL DEFAULT gen_random_uuid(),
    landlord_id    UUID           NOT NULL,
    tenant_id      UUID           NOT NULL,
    unit_id        UUID           NOT NULL,
    start_date     DATE           NOT NULL,
    move_out_date  DATE,
    rent_amount    DECIMAL(12, 2) NOT NULL,
    deposit_amount DECIMAL(12, 2) NOT NULL,
    status         VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_rental_agreements PRIMARY KEY (id),
    CONSTRAINT fk_agreements_landlord FOREIGN KEY (landlord_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_agreements_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_agreements_unit FOREIGN KEY (unit_id) REFERENCES rental_units (id) ON DELETE RESTRICT,
    CONSTRAINT chk_agreements_status CHECK (status IN ('ACTIVE', 'TERMINATED'))
);