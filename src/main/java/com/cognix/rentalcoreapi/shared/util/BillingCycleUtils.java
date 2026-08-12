package com.cognix.rentalcoreapi.shared.util;

import com.cognix.rentalcoreapi.modules.agreements.model.BillingModel;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class BillingCycleUtils {

    /**
     * Compute the current cycle start date for an agreement.
     * <p>
     * ADVANCE: current in-progress cycle (e.g. today Apr 25, billingDay 10 → Apr 10)
     * ARREARS: last completed cycle (e.g. today May 10, billingDay 15 → Mar 15)
     * because payment is collected at END of cycle — Mar 15–Apr 14 is due
     */
    public static LocalDate currentCycleStart(RentalAgreement agreement) {
        if (agreement.getStartDate() == null) return null;

        LocalDate today = LocalDate.now();
        int billingDay = agreement.getBillingDay();

        LocalDate cycleStart = today.withDayOfMonth(
                Math.min(billingDay, today.lengthOfMonth()));

        if (cycleStart.isAfter(today)) {
            cycleStart = cycleStart.minusMonths(1)
                    .withDayOfMonth(Math.min(billingDay,
                            cycleStart.minusMonths(1).lengthOfMonth()));
        }

        if (agreement.getBillingModel() == BillingModel.ARREARS) {
            cycleStart = cycleStart.minusMonths(1)
                    .withDayOfMonth(Math.min(billingDay,
                            cycleStart.minusMonths(1).lengthOfMonth()));
        }

        // Never go before startDate
        if (cycleStart.isBefore(agreement.getStartDate())) {
            cycleStart = agreement.getStartDate();
        }

        // ARREARS — if the computed cycle hasn't ended yet, no cycle is due
        if (agreement.getBillingModel() == BillingModel.ARREARS) {
            LocalDate computedCycleEnd = cycleEnd(cycleStart, billingDay);
            if (today.isBefore(computedCycleEnd)
                    && cycleStart.isEqual(agreement.getStartDate())) {
                // First cycle not yet complete — nothing due
                return null;
            }
        }

        return cycleStart;
    }

    /**
     * Compute cycle end date given a cycle start date.
     * e.g. Apr 15 → May 14
     */
    public static LocalDate cycleEnd(LocalDate cycleStart, int billingDay) {
        LocalDate next = cycleStart.plusMonths(1);
        int safeDay = Math.min(billingDay,
                next.getMonth().length(next.isLeapYear()));
        return next.withDayOfMonth(safeDay).minusDays(1);
    }

    /**
     * Whether rent for a cycle is actually owed as of {@code today}. Used
     * everywhere "is this rent due yet?" is asked — the balance calculation and
     * the cycle list shown when recording a payment — so a future cycle offered
     * only for paying ahead is never counted as arrears.
     * <p>
     * ADVANCE: due once the period has started (rent paid at the start).
     * ARREARS: due only once the period has ended (rent paid at the end).
     */
    public static boolean isDue(BillingModel model, LocalDate periodStart,
                                LocalDate periodEnd, LocalDate today) {
        return model == BillingModel.ARREARS
                ? periodEnd.isBefore(today)
                : !periodStart.isAfter(today);
    }

    /**
     * Count how many billing cycles have elapsed and are DUE.
     * <p>
     * ADVANCE: current in-progress cycle counts immediately (paid at start)
     * ARREARS: only completed cycles count (paid at end)
     * <p>
     * currentCycleStart() already handles the ADVANCE/ARREARS distinction,
     * so elapsed is always: months between firstCycleStart and currentCycleStart + 1
     */
    public static long cyclesElapsed(RentalAgreement agreement) {
        return cyclesElapsedFromDate(
                effectiveStartDate(agreement),
                agreement.getBillingDay(),
                agreement.getBillingModel()
        );
    }

    /**
     * The date cycle-tracking effectively begins for an agreement — its
     * explicit startDate, or the agreement's own createdAt if none was set.
     * Shared by every computation that needs to know which payments/cycles
     * count as "tracked" (cycle counting, balance totals, cycle generation),
     * so they can't drift apart on which agreements they consider started.
     */
    public static LocalDate effectiveStartDate(RentalAgreement agreement) {
        return agreement.getStartDate() != null
                ? agreement.getStartDate()
                : agreement.getCreatedAt().toLocalDate();
    }

    private static long cyclesElapsedFromDate(
            LocalDate fromDate, int billingDay, BillingModel model) {

        LocalDate today = LocalDate.now();

        if (fromDate.isAfter(today)) return 0;

        int safeDay = Math.min(billingDay,
                fromDate.getMonth().length(fromDate.isLeapYear()));
        LocalDate firstCycleStart = fromDate.withDayOfMonth(safeDay);

        if (fromDate.getDayOfMonth() > safeDay) {
            firstCycleStart = firstCycleStart.plusMonths(1);
        }

        if (firstCycleStart.isAfter(today)) return 0;

        LocalDate currentCycle = currentCycleStart(
                buildTemp(fromDate, billingDay, model));

        if (currentCycle == null || currentCycle.isBefore(firstCycleStart)) {
            return 0;
        }

        if (model == BillingModel.ARREARS) {
            // For ARREARS — only count cycles that have fully completed
            // i.e. cycleEnd < today
            LocalDate currentCycleEnd = cycleEnd(currentCycle, billingDay);

            // If the current cycle hasn't ended yet, don't count it
            if (today.isBefore(currentCycleEnd)) {
                // Go back one more cycle
                LocalDate previousCycle = currentCycle.minusMonths(1)
                        .withDayOfMonth(Math.min(billingDay,
                                currentCycle.minusMonths(1).lengthOfMonth()));

                if (previousCycle.isBefore(firstCycleStart)) {
                    return 0; // No completed cycles yet
                }

                long months = ChronoUnit.MONTHS.between(firstCycleStart, previousCycle) + 1;
                return Math.max(0, months);
            }
        }

        long months = ChronoUnit.MONTHS.between(firstCycleStart, currentCycle) + 1;
        return Math.max(0, months);
    }

    /**
     * A minimal, unsaved RentalAgreement carrying just enough fields to run
     * cycle math against an explicit start date — used wherever an
     * agreement's own startDate is null and effectiveStartDate() (createdAt)
     * must stand in for it instead.
     */
    public static RentalAgreement buildTemp(
            LocalDate fromDate, int billingDay, BillingModel model) {
        RentalAgreement temp = new RentalAgreement();
        temp.setStartDate(fromDate);
        temp.setBillingDay(billingDay);
        temp.setBillingModel(model);
        return temp;
    }

    /**
     * Get next cycle start date from a given cycle start.
     */
    public static LocalDate nextCycleStart(LocalDate cycleStart, int billingDay) {
        LocalDate next = cycleStart.plusMonths(1);
        int safeDay = Math.min(billingDay,
                next.getMonth().length(next.isLeapYear()));
        return next.withDayOfMonth(safeDay);
    }
}