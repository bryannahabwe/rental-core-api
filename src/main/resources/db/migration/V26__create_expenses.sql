CREATE TABLE expenses
(
    id           UUID           NOT NULL DEFAULT gen_random_uuid(),
    landlord_id  UUID           NOT NULL,
    property_id  UUID           NOT NULL,
    unit_id      UUID,
    expense_date DATE           NOT NULL,
    amount       DECIMAL(12, 2) NOT NULL,
    category     VARCHAR(255)   NOT NULL,
    vendor       VARCHAR(255),
    receipt_url  VARCHAR(1024),
    reference    VARCHAR(255),
    notes        TEXT,
    created_at   TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_expenses PRIMARY KEY (id),
    CONSTRAINT fk_expenses_landlord FOREIGN KEY (landlord_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_expenses_property FOREIGN KEY (property_id) REFERENCES properties (id) ON DELETE RESTRICT,
    -- Optional unit attribution; if the unit is deleted the expense stays on
    -- its property rather than being lost.
    CONSTRAINT fk_expenses_unit FOREIGN KEY (unit_id) REFERENCES rental_units (id) ON DELETE SET NULL
);

CREATE INDEX idx_expenses_property ON expenses (property_id);
