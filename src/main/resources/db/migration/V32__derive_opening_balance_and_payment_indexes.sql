-- Make `opening_balance` derivable, so a payment can be corrected.
--
-- `opening_balance` is a running figure with three independent contributors:
-- the arrears/credit entered when the agreement was created, every payment
-- filed against a period before cycle tracking begins (`+= amount`), and any
-- deposit applied at move-out (`+= applied`). Because the as-entered figure was
-- never recorded separately, none of those contributions can be reversed: you
-- cannot subtract a payment's effect without knowing what the balance would
-- have been without it, and `PUT /agreements` overwrites the whole figure
-- absolutely, destroying any relationship a subtraction could rely on.
--
-- Split the two. `opening_balance_entered` is what the landlord typed and only
-- they change it; `opening_balance` stays the effective figure every balance
-- calculation already reads, recomputed from the payment rows on every write.
-- Editing or deleting an arrears payment then needs no compensating arithmetic
-- at all — the figure simply falls out of the rows that remain.
--
-- This also repairs a live defect: `PUT /agreements` can move `start_date`,
-- which moves BillingCycleUtils.effectiveStartDate and silently re-classifies
-- already-recorded payments across the arrears/cycle boundary with no
-- compensating adjustment. Derived, that self-heals on the next write.

ALTER TABLE rental_agreements ADD COLUMN opening_balance_entered DECIMAL(12, 2);

UPDATE rental_agreements a
SET opening_balance_entered = a.opening_balance
    - COALESCE((SELECT SUM(p.amount)
                FROM payments p
                WHERE p.agreement_id = a.id
                  AND p.source = 'CASH'
                  AND p.period_start_date < COALESCE(a.start_date, a.created_at::date)), 0)
    - COALESCE(a.deposit_applied, 0);

ALTER TABLE rental_agreements ALTER COLUMN opening_balance_entered SET DEFAULT 0;
ALTER TABLE rental_agreements ALTER COLUMN opening_balance_entered SET NOT NULL;

-- Which CASH payment a rollover row's credit came from. Rollover rows carried
-- no link back to their funder — only a shared payment_date and a reference
-- naming the day before their own cycle, which identifies nothing — so a
-- refused edit on one could not name the payment to correct instead. The
-- CASCADE is a second line of defence: derived credit can never outlive the
-- cash that funded it.
ALTER TABLE payments ADD COLUMN funded_by_payment_id UUID;
ALTER TABLE payments ADD CONSTRAINT fk_payments_funded_by
    FOREIGN KEY (funded_by_payment_id) REFERENCES payments (id) ON DELETE CASCADE;

-- Replaying an agreement reads `WHERE agreement_id = ? ORDER BY created_at, id`
-- on every payment write; `payments` carried only idx_payments_property.
CREATE INDEX idx_payments_agreement_created ON payments (agreement_id, created_at, id);
