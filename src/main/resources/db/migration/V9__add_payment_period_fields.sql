ALTER TABLE payments
    ADD COLUMN period_month    INT,
    ADD COLUMN period_year     INT,
    ADD COLUMN expected_amount DECIMAL(12,2),
    ADD COLUMN overpayment     DECIMAL(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN source          VARCHAR(20)   NOT NULL DEFAULT 'CASH';

UPDATE payments
SET
    period_month    = EXTRACT(MONTH FROM payment_date),
    period_year     = EXTRACT(YEAR FROM payment_date),
    expected_amount = amount;

ALTER TABLE payments
    ALTER COLUMN period_month    SET NOT NULL,
    ALTER COLUMN period_year     SET NOT NULL,
    ALTER COLUMN expected_amount SET NOT NULL;

CREATE UNIQUE INDEX uq_payment_rollover
    ON payments (agreement_id, period_month, period_year)
    WHERE source = 'ROLLOVER';