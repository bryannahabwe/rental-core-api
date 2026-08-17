-- Managed pick-lists for expenses. Both account-scoped and unique per account
-- (case-insensitive). Categories are FK'd from expenses; payment methods are
-- referenced by name (expenses.method stores the name string).
CREATE TABLE expense_categories
(
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    landlord_id UUID         NOT NULL,
    name        VARCHAR(120) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_expense_categories PRIMARY KEY (id),
    CONSTRAINT fk_expense_categories_landlord FOREIGN KEY (landlord_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX uq_expense_categories_landlord_name ON expense_categories (landlord_id, LOWER(name));

CREATE TABLE payment_methods
(
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    landlord_id UUID         NOT NULL,
    name        VARCHAR(120) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_payment_methods PRIMARY KEY (id),
    CONSTRAINT fk_payment_methods_landlord FOREIGN KEY (landlord_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX uq_payment_methods_landlord_name ON payment_methods (landlord_id, LOWER(name));

-- Seed defaults for every existing account owner (new accounts are seeded in code).
INSERT INTO expense_categories (landlord_id, name)
SELECT u.id, c.name
FROM users u
         CROSS JOIN (VALUES ('Repairs & maintenance'), ('Utilities'), ('Staff & wages'),
                            ('Management fees'), ('Security'), ('Cleaning'), ('Taxes & levies'),
                            ('Insurance'), ('Supplies'), ('Other')) AS c(name)
WHERE u.id = u.account_owner_id
  AND NOT EXISTS (SELECT 1 FROM expense_categories ec
                  WHERE ec.landlord_id = u.id AND LOWER(ec.name) = LOWER(c.name));

INSERT INTO payment_methods (landlord_id, name)
SELECT u.id, m.name
FROM users u
         CROSS JOIN (VALUES ('Cash'), ('Mobile Money'), ('Bank Transfer'), ('Cheque')) AS m(name)
WHERE u.id = u.account_owner_id
  AND NOT EXISTS (SELECT 1 FROM payment_methods pm
                  WHERE pm.landlord_id = u.id AND LOWER(pm.name) = LOWER(m.name));

-- Backfill a category row for any existing free-form expense category string.
INSERT INTO expense_categories (landlord_id, name)
SELECT DISTINCT e.landlord_id, TRIM(e.category)
FROM expenses e
WHERE e.category IS NOT NULL
  AND TRIM(e.category) <> ''
  AND NOT EXISTS (SELECT 1 FROM expense_categories ec
                  WHERE ec.landlord_id = e.landlord_id AND LOWER(ec.name) = LOWER(TRIM(e.category)));

-- Evolve expenses: category string → category_id FK, add method + paid_by.
ALTER TABLE expenses ADD COLUMN category_id UUID;
ALTER TABLE expenses ADD COLUMN method VARCHAR(120);
ALTER TABLE expenses ADD COLUMN paid_by VARCHAR(255);

UPDATE expenses e
SET category_id = ec.id
FROM expense_categories ec
WHERE ec.landlord_id = e.landlord_id
  AND LOWER(ec.name) = LOWER(TRIM(e.category));

-- Existing expenses carried no payment method — default them to Cash.
UPDATE expenses SET method = 'Cash' WHERE method IS NULL;

ALTER TABLE expenses ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE expenses ALTER COLUMN method SET NOT NULL;
ALTER TABLE expenses ADD CONSTRAINT fk_expenses_category
    FOREIGN KEY (category_id) REFERENCES expense_categories (id) ON DELETE RESTRICT;
CREATE INDEX idx_expenses_category ON expenses (category_id);

-- Drop the now-replaced free-form columns.
ALTER TABLE expenses DROP COLUMN category;
ALTER TABLE expenses DROP COLUMN vendor;
ALTER TABLE expenses DROP COLUMN reference;
