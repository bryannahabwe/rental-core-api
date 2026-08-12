-- Zero out `overpayment` on existing ROLLOVER rows.
--
-- A ROLLOVER row's `amount` is already the credit retained by that cycle
-- (capped at one month's rent); any surplus is carried by the NEXT rollover
-- row's `amount`, not by this row's `overpayment`. Older rollover rows stored
-- the surplus in `overpayment` too, so the per-cycle retained figure
-- (amount - overpayment) collapsed to 0 for every middle cycle in a chain
-- (e.g. a fully-covered month showed UGX 0 instead of the rent).
--
-- The recording code now writes overpayment = 0 on rollover rows; this fixes
-- the rows written before that change. Safe: `overpayment` on a ROLLOVER row
-- is never netted out of any cumulative total (only CASH overpayment is), so
-- zeroing it only corrects the per-cycle retained display.
UPDATE payments
SET overpayment = 0
WHERE source = 'ROLLOVER'
  AND overpayment <> 0;
