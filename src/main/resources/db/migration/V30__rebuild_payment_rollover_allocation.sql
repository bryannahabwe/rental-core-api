-- Rebuild how received cash is allocated across billing cycles.
--
-- Two defects sized rollover credit against a cycle's full rent instead of
-- against what that cycle still needed:
--
--   1. Recording a payment computed its spill as `amount - rent`. A cycle
--      already holding part of its rent was charged for that part a second
--      time, and the difference never entered the rollover chain at all — it
--      simply stopped existing as credit. A 610k lump sum against an April
--      already holding 110k rolled 430k instead of 540k, so July ended 110k
--      short of a month the tenant had in fact paid for.
--
--   2. Placing that spill skipped any cycle already carrying a rollover row,
--      on the assumption that rollover rows are always written for a full
--      month. The last row in a chain is partial by construction, so a
--      part-covered cycle was jumped over entirely and the credit landed a
--      cycle too late, leaving a permanent hole behind it.
--
-- Neither defect changes an agreement's totals — the same cash was always
-- counted — but both misplace it across cycles, so periods read UNPAID or
-- PARTIAL when the money for them had already been received, and tenants were
-- billed again for rent they had settled.
--
-- The repair replays the allocation. ROLLOVER rows are derived data: they
-- re-label cash already recorded on the CASH row that spawned them, which is
-- why every cumulative total excludes them. So they can be discarded and
-- rewritten from the CASH rows, which are the record of money actually
-- received and are never modified here beyond `overpayment` — itself a derived
-- figure recording how much of that payment spilled out of its own cycle.
--
-- Replay runs in `created_at` order, the order the payments were originally
-- recorded in, so the outcome is what the corrected code would have produced
-- had it always been in place. Every agreement is rebuilt rather than only
-- those that look wrong: defect 2 leaves no signature distinguishable from a
-- tenant legitimately paying a future cycle ahead, so there is no predicate
-- that separates damaged data from sound data. On sound data the replay is a
-- no-op in substance — the same credits land on the same cycles.

-- Cycle end for a cycle starting on `cycle_start`, mirroring
-- BillingCycleUtils.cycleEnd: step a month forward (clamping to the shorter
-- month, as java.time does), take the billing day or that month's last day,
-- whichever comes first, and stop the day before.
CREATE OR REPLACE FUNCTION pg_temp.cycle_end(cycle_start DATE, billing_day INT)
    RETURNS DATE AS
$fn$
DECLARE
    month_head DATE;
    days_in    INT;
BEGIN
    month_head := date_trunc('month', cycle_start + INTERVAL '1 month')::date;
    days_in := EXTRACT(DAY FROM (month_head + INTERVAL '1 month' - INTERVAL '1 day'))::int;
    RETURN month_head + (LEAST(billing_day, days_in) - 1) - 1;
END;
$fn$ LANGUAGE plpgsql IMMUTABLE;

DO
$rebuild$
DECLARE
    -- Matches MAX_ROLLOVER_LOOKAHEAD_CYCLES in PaymentService.
    max_lookahead CONSTANT INT := 120;

    agr           RECORD;
    pay           RECORD;
    rent          NUMERIC(12, 2);
    billing_day   INT;
    cutoff        DATE;
    cycle_need    NUMERIC(12, 2);
    applied       NUMERIC(12, 2);
    spill         NUMERIC(12, 2);
    credit        NUMERIC(12, 2);
    cur_start     DATE;
    cur_end       DATE;
    depth         INT;
    discarded     BIGINT := 0;
    written       BIGINT := 0;
    touched       BIGINT := 0;
BEGIN
    -- Running retained total per cycle for the agreement being rebuilt:
    -- SUM(amount - overpayment), the figure the ledger reads a cycle by.
    CREATE TEMP TABLE rebuild_cycle
    (
        cycle_start DATE PRIMARY KEY,
        retained    NUMERIC(12, 2) NOT NULL
    ) ON COMMIT DROP;

    FOR agr IN
        SELECT a.id, a.rent_amount, a.billing_day, a.start_date, a.created_at
        FROM rental_agreements a
        WHERE EXISTS (SELECT 1 FROM payments p WHERE p.agreement_id = a.id)
        ORDER BY a.id
        LOOP
            rent := COALESCE(agr.rent_amount, 0);
            billing_day := COALESCE(agr.billing_day, 1);
            -- BillingCycleUtils.effectiveStartDate: the date cycle tracking
            -- begins. Payments filed before it are opening arrears, not rent
            -- for any cycle.
            cutoff := COALESCE(agr.start_date, agr.created_at::date);

            TRUNCATE rebuild_cycle;

            DELETE FROM payments
            WHERE agreement_id = agr.id
              AND source = 'ROLLOVER';
            GET DIAGNOSTICS credit = ROW_COUNT;
            discarded := discarded + credit;

            FOR pay IN
                SELECT id, landlord_id, property_id, tenant_id, unit_id, agreement_id,
                       payment_date, amount, period_start_date, period_end_date,
                       overpayment, created_at
                FROM payments
                WHERE agreement_id = agr.id
                  AND source <> 'ROLLOVER'
                ORDER BY created_at, id
                LOOP
                    -- Opening arrears: applied to the agreement's opening
                    -- balance when recorded, never to a cycle. Left alone.
                    IF pay.period_start_date < cutoff THEN
                        IF pay.overpayment <> 0 THEN
                            UPDATE payments SET overpayment = 0 WHERE id = pay.id;
                            touched := touched + 1;
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
                        touched := touched + 1;
                    END IF;

                    -- Carry the remainder forward, each cycle absorbing only
                    -- what it still lacks. A zero rent would leave every cycle
                    -- needing nothing, so there is nowhere to carry it.
                    IF rent > 0 THEN
                        cur_start := pay.period_end_date + 1;
                        depth := 0;

                        WHILE spill > 0 AND depth < max_lookahead
                            LOOP
                                cur_end := pg_temp.cycle_end(cur_start, billing_day);

                                cycle_need := GREATEST(rent - COALESCE(
                                        (SELECT retained FROM rebuild_cycle
                                         WHERE cycle_start = cur_start), 0), 0);

                                IF cycle_need > 0 THEN
                                    credit := LEAST(spill, cycle_need);

                                    INSERT INTO payments (landlord_id, property_id, tenant_id,
                                                          unit_id, agreement_id, payment_date,
                                                          amount, method, period_start_date,
                                                          period_end_date, expected_amount,
                                                          overpayment, source, reference,
                                                          notes, created_at)
                                    VALUES (pay.landlord_id, pay.property_id, pay.tenant_id,
                                            pay.unit_id, pay.agreement_id, pay.payment_date,
                                            credit, 'CASH', cur_start,
                                            cur_end, rent,
                                            -- The remainder is carried by the NEXT
                                            -- rollover row, not by this one: storing
                                            -- it here would net this cycle's retained
                                            -- figure (amount - overpayment) to zero.
                                            0, 'ROLLOVER',
                                            'Rollover from ' || to_char(cur_start - 1, 'YYYY-MM-DD'),
                                            NULL,
                                            -- Sorts immediately after the payment that
                                            -- funded it, as a freshly recorded chain does.
                                            pay.created_at + ((depth + 1) * INTERVAL '1 microsecond'));

                                    INSERT INTO rebuild_cycle (cycle_start, retained)
                                    VALUES (cur_start, credit)
                                    ON CONFLICT (cycle_start)
                                        DO UPDATE SET retained = rebuild_cycle.retained + EXCLUDED.retained;

                                    spill := spill - credit;
                                    written := written + 1;
                                END IF;

                                cur_start := cur_end + 1;
                                depth := depth + 1;
                            END LOOP;
                    END IF;
                END LOOP;
        END LOOP;

    RAISE NOTICE 'Rollover allocation rebuilt: % rows discarded, % rows written, % cash rows re-marked.',
        discarded, written, touched;
END
$rebuild$;
