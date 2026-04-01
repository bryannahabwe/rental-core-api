ALTER TABLE rental_agreements
    ADD COLUMN tenant_type     VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    ADD COLUMN opening_balance DECIMAL(12,2) NOT NULL DEFAULT 0;