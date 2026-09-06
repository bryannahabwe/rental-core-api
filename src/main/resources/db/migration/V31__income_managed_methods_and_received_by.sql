-- Bring income to the pattern expenses already set in V26.
--
-- `other_income.method` was a hardcoded enum (CASH, MOBILE_MONEY, …) while
-- expenses draw theirs from the account-managed `payment_methods` list — so
-- Settings → Payment Methods governed only half the money-in/money-out surface
-- despite its page already saying it covered both. Income now stores the
-- method name string, normalized on write, exactly as expenses do.
--
-- The person is a separate concern from the tender type, and income had no
-- field for it at all: the form's "Received by" select was in fact the method
-- picker under the wrong label. `received_by` is the income counterpart of
-- `expenses.paid_by` — free text, optional, no user FK (a caretaker who took
-- the cash may not hold an account).

ALTER TABLE other_income DROP CONSTRAINT chk_other_income_method;
ALTER TABLE other_income ALTER COLUMN method TYPE VARCHAR(120);
ALTER TABLE other_income ALTER COLUMN method SET DEFAULT 'Cash';

-- Enum value → display name. These are exactly the four names
-- PaymentMethodService.DEFAULT_METHODS seeds and V26 backfilled, so every
-- converted value already matches an option on every pre-existing account.
UPDATE other_income
SET method = CASE method
                 WHEN 'CASH' THEN 'Cash'
                 WHEN 'MOBILE_MONEY' THEN 'Mobile Money'
                 WHEN 'BANK_TRANSFER' THEN 'Bank Transfer'
                 WHEN 'CHEQUE' THEN 'Cheque'
                 ELSE method
    END;

-- Belt and braces for accounts created before V26's seeding, or whose option
-- was renamed since: every method an income row now names must be pickable.
INSERT INTO payment_methods (landlord_id, name)
SELECT DISTINCT o.landlord_id, TRIM(o.method)
FROM other_income o
WHERE TRIM(COALESCE(o.method, '')) <> ''
  AND NOT EXISTS (SELECT 1
                  FROM payment_methods pm
                  WHERE pm.landlord_id = o.landlord_id
                    AND LOWER(pm.name) = LOWER(TRIM(o.method)));

ALTER TABLE other_income ADD COLUMN received_by VARCHAR(255);
