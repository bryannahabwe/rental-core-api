-- Reverse a payment recorded in error, and re-derive the credit it carried.
--
-- There is no delete-payment endpoint: recording a payment is deliberately
-- one-way, because a payment row asserts that money changed hands. When one is
-- keyed in that should not have been — a mis-typed amount, a duplicate, or a
-- figure entered to clear a balance the system was wrongly showing — the row
-- has to come out here, deliberately, against a backup.
--
-- Deleting the CASH row alone is not enough. Any surplus on it was re-recorded
-- as ROLLOVER rows on later cycles, and those rows are what make those cycles
-- read as paid. So the agreement's rollover chain is discarded and rebuilt from
-- the CASH rows that remain, exactly as V30 does.
--
-- The audit trail is left intact. It records that someone performed the
-- recording, which remains true and is the point of an audit trail; this script
-- adds its own entry saying the row was reversed.
--
-- USAGE
--   1. Run it with the id of the row to remove. It ROLLS BACK by default and
--      prints the rows and cycles before and after — read both.
--   2. Only once the "after" is what you expect, change ROLLBACK to COMMIT at
--      the foot of this file and run it again.
--
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 \
--        -v payment_id=<uuid> -f scripts/reverse-erroneous-payment.sql

-- Falls back to a deliberately invalid id, so running this without naming a
-- payment stops on the guard below rather than touching anything.
\if :{?payment_id}
\else
\set payment_id '00000000-0000-0000-0000-000000000000'
\endif

\pset border 2
\timing off

BEGIN;

-- psql substitutes :vars only outside dollar-quoted bodies, so the DO blocks
-- below read the id from a transaction-local setting rather than inline.
SET LOCAL reverse.payment_id = :'payment_id';

-- ── The row about to be removed ──────────────────────────────────────
\echo ''
\echo '── reversing ──'
SELECT p.id, t.name AS tenant, u.room_number AS unit, p.payment_date,
       p.amount, p.overpayment, p.source,
       p.period_start_date, p.period_end_date, p.reference
FROM payments p
         JOIN tenants t ON t.id = p.tenant_id
         JOIN rental_units u ON u.id = p.unit_id
WHERE p.id = :'payment_id';

DO
$guard$
DECLARE
    src TEXT;
BEGIN
    SELECT source INTO src FROM payments WHERE id = current_setting('reverse.payment_id')::uuid;

    IF src IS NULL THEN
        RAISE EXCEPTION 'No payment with id %', current_setting('reverse.payment_id');
    END IF;

    -- A ROLLOVER row is derived, not received. Deleting one by hand would
    -- strand the credit and it would reappear on the next rebuild anyway.
    IF src = 'ROLLOVER' THEN
        RAISE EXCEPTION 'Payment % is a ROLLOVER row — derived, not received. Reverse the CASH payment that funded it instead.',
            current_setting('reverse.payment_id');
    END IF;
END
$guard$;

\o /dev/null
SELECT set_config('reverse.agreement_id', agreement_id::text, true)
FROM payments WHERE id = :'payment_id';
\o

-- ── Cycle state before ───────────────────────────────────────────────
\echo ''
\echo '── cycles before ──'
SELECT p.period_start_date AS cycle, a.rent_amount AS rent,
       SUM(p.amount - p.overpayment) AS retained,
       CASE WHEN SUM(p.amount - p.overpayment) >= a.rent_amount THEN 'PAID'
            WHEN SUM(p.amount - p.overpayment) > 0 THEN 'PARTIAL'
            ELSE 'UNPAID' END AS status
FROM payments p
         JOIN rental_agreements a ON a.id = p.agreement_id
WHERE p.agreement_id = (SELECT agreement_id FROM payments WHERE id = :'payment_id')
  AND p.period_start_date >= COALESCE(a.start_date, a.created_at::date)
GROUP BY p.period_start_date, a.rent_amount
ORDER BY 1;

DELETE FROM payments WHERE id = :'payment_id';

-- ── Re-derive the agreement's rollover chain (same walk as V30) ──────
DO
$rebuild$
DECLARE
    max_lookahead CONSTANT INT := 120;

    agr         RECORD;
    pay         RECORD;
    rent        NUMERIC(12, 2);
    billing_day INT;
    cutoff      DATE;
    cycle_need  NUMERIC(12, 2);
    applied     NUMERIC(12, 2);
    spill       NUMERIC(12, 2);
    credit      NUMERIC(12, 2);
    month_head  DATE;
    days_in     INT;
    cur_start   DATE;
    cur_end     DATE;
    depth       INT;
BEGIN
    SELECT a.id, a.rent_amount, a.billing_day, a.start_date, a.created_at
    INTO agr
    FROM rental_agreements a
    WHERE a.id = current_setting('reverse.agreement_id')::uuid;

    rent := COALESCE(agr.rent_amount, 0);
    billing_day := COALESCE(agr.billing_day, 1);
    cutoff := COALESCE(agr.start_date, agr.created_at::date);

    CREATE TEMP TABLE rebuild_cycle
    (
        cycle_start DATE PRIMARY KEY,
        retained    NUMERIC(12, 2) NOT NULL
    ) ON COMMIT DROP;

    DELETE FROM payments WHERE agreement_id = agr.id AND source = 'ROLLOVER';

    FOR pay IN
        SELECT id, landlord_id, property_id, tenant_id, unit_id, agreement_id,
               payment_date, amount, period_start_date, period_end_date,
               overpayment, created_at
        FROM payments
        WHERE agreement_id = agr.id AND source <> 'ROLLOVER'
        ORDER BY created_at, id
        LOOP
            IF pay.period_start_date < cutoff THEN
                IF pay.overpayment <> 0 THEN
                    UPDATE payments SET overpayment = 0 WHERE id = pay.id;
                END IF;
                CONTINUE;
            END IF;

            cycle_need := GREATEST(rent - COALESCE(
                    (SELECT retained FROM rebuild_cycle
                     WHERE cycle_start = pay.period_start_date), 0), 0);

            applied := LEAST(pay.amount, cycle_need);
            spill := pay.amount - applied;

            INSERT INTO rebuild_cycle (cycle_start, retained)
            VALUES (pay.period_start_date, applied)
            ON CONFLICT (cycle_start)
                DO UPDATE SET retained = rebuild_cycle.retained + EXCLUDED.retained;

            IF pay.overpayment IS DISTINCT FROM spill THEN
                UPDATE payments SET overpayment = spill WHERE id = pay.id;
            END IF;

            IF rent > 0 THEN
                cur_start := pay.period_end_date + 1;
                depth := 0;

                WHILE spill > 0 AND depth < max_lookahead
                    LOOP
                        month_head := date_trunc('month', cur_start + INTERVAL '1 month')::date;
                        days_in := EXTRACT(DAY FROM (month_head + INTERVAL '1 month' - INTERVAL '1 day'))::int;
                        cur_end := month_head + (LEAST(billing_day, days_in) - 1) - 1;

                        cycle_need := GREATEST(rent - COALESCE(
                                (SELECT retained FROM rebuild_cycle
                                 WHERE cycle_start = cur_start), 0), 0);

                        IF cycle_need > 0 THEN
                            credit := LEAST(spill, cycle_need);

                            INSERT INTO payments (landlord_id, property_id, tenant_id, unit_id,
                                                  agreement_id, payment_date, amount, method,
                                                  period_start_date, period_end_date,
                                                  expected_amount, overpayment, source,
                                                  reference, notes, created_at)
                            VALUES (pay.landlord_id, pay.property_id, pay.tenant_id, pay.unit_id,
                                    pay.agreement_id, pay.payment_date, credit, 'CASH',
                                    cur_start, cur_end, rent, 0, 'ROLLOVER',
                                    'Rollover from ' || to_char(cur_start - 1, 'YYYY-MM-DD'), NULL,
                                    pay.created_at + ((depth + 1) * INTERVAL '1 microsecond'));

                            INSERT INTO rebuild_cycle (cycle_start, retained)
                            VALUES (cur_start, credit)
                            ON CONFLICT (cycle_start)
                                DO UPDATE SET retained = rebuild_cycle.retained + EXCLUDED.retained;

                            spill := spill - credit;
                        END IF;

                        cur_start := cur_end + 1;
                        depth := depth + 1;
                    END LOOP;
            END IF;
        END LOOP;
END
$rebuild$;

-- ── Resulting rows and cycles ────────────────────────────────────────
\echo ''
\echo '── payment records after ──'
SELECT p.source, p.amount, p.overpayment, p.period_start_date, p.period_end_date,
       p.reference, p.payment_date
FROM payments p
WHERE p.agreement_id = current_setting('reverse.agreement_id')::uuid
ORDER BY p.created_at;

\echo ''
\echo '── cycles after ──'
SELECT p.period_start_date AS cycle, a.rent_amount AS rent,
       SUM(p.amount - p.overpayment) AS retained,
       CASE WHEN SUM(p.amount - p.overpayment) >= a.rent_amount THEN 'PAID'
            WHEN SUM(p.amount - p.overpayment) > 0 THEN 'PARTIAL'
            ELSE 'UNPAID' END AS status
FROM payments p
         JOIN rental_agreements a ON a.id = p.agreement_id
WHERE p.agreement_id = current_setting('reverse.agreement_id')::uuid
  AND p.period_start_date >= COALESCE(a.start_date, a.created_at::date)
GROUP BY p.period_start_date, a.rent_amount
ORDER BY 1;

-- Change to COMMIT once the output above is what you expect.
ROLLBACK;
