ALTER TABLE payments
    ADD COLUMN period_start_date DATE,
    ADD COLUMN period_end_date   DATE;

UPDATE payments SET
    period_start_date = (period_year::text || '-' ||
    LPAD(period_month::text, 2, '0') || '-01')::DATE,
  period_end_date = ((period_year::text || '-' ||
    LPAD(period_month::text, 2, '0') || '-01')::DATE
    + INTERVAL '1 month' - INTERVAL '1 day')::DATE
WHERE period_month IS NOT NULL AND period_year IS NOT NULL;

ALTER TABLE payments
    ALTER COLUMN period_start_date SET NOT NULL,
    ALTER COLUMN period_end_date   SET NOT NULL;