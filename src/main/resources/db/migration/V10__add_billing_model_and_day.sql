ALTER TABLE rental_agreements
    ADD COLUMN billing_day   INT         NOT NULL DEFAULT 1,
    ADD COLUMN billing_model VARCHAR(20) NOT NULL DEFAULT 'ADVANCE';

UPDATE rental_agreements
SET billing_day = LEAST(EXTRACT(DAY FROM start_date)::INT, 28)
WHERE start_date IS NOT NULL;