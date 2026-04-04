package com.cognix.rentalcoreapi.shared.util;

import com.cognix.rentalcoreapi.modules.agreements.model.BillingModel;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class BillingCycleUtils {

    /**
     * Compute the current cycle start date for an agreement.
     * e.g. billing day = 10, today = Apr 25 → Apr 10
     *      billing day = 10, today = Apr 8  → Mar 10
     */
    public static LocalDate currentCycleStart(RentalAgreement agreement) {
        LocalDate today = LocalDate.now();
        int billingDay = agreement.getBillingDay();

        // Safe billing day for short months
        int safeDay = Math.min(billingDay,
                today.getMonth().length(today.isLeapYear()));

        LocalDate candidate = today.withDayOfMonth(safeDay);
        if (candidate.isAfter(today)) {
            candidate = candidate.minusMonths(1);
            safeDay = Math.min(billingDay,
                    candidate.getMonth().length(candidate.isLeapYear()));
            candidate = candidate.withDayOfMonth(safeDay);
        }
        return candidate;
    }

    /**
     * Compute cycle end date given a cycle start date.
     * e.g. Apr 10 → May 9
     */
    public static LocalDate cycleEnd(LocalDate cycleStart, int billingDay) {
        LocalDate next = cycleStart.plusMonths(1);
        int safeDay = Math.min(billingDay,
                next.getMonth().length(next.isLeapYear()));
        return next.withDayOfMonth(safeDay).minusDays(1);
    }

    /**
     * Count how many billing cycles have elapsed and are DUE.
     * ADVANCE: current cycle is immediately due on start day
     * ARREARS: only completed cycles are due
     */
    public static long cyclesElapsed(RentalAgreement agreement) {
        if (agreement.getStartDate() == null) {
            // EXISTING tenant with no start date — use createdAt
            return cyclesElapsedFromDate(
                    agreement.getCreatedAt().toLocalDate(),
                    agreement.getBillingDay(),
                    agreement.getBillingModel()
            );
        }
        return cyclesElapsedFromDate(
                agreement.getStartDate(),
                agreement.getBillingDay(),
                agreement.getBillingModel()
        );
    }

    private static long cyclesElapsedFromDate(
            LocalDate fromDate, int billingDay, BillingModel model) {

        LocalDate today = LocalDate.now();

        // If startDate is in the future, no cycles have started yet
        if (fromDate.isAfter(today)) {
            return 0;
        }

        int safeDay = Math.min(billingDay,
                fromDate.getMonth().length(fromDate.isLeapYear()));
        LocalDate firstCycleStart = fromDate.withDayOfMonth(safeDay);

        if (fromDate.getDayOfMonth() > safeDay) {
            firstCycleStart = firstCycleStart.plusMonths(1);
        }

        // If firstCycleStart is still in the future, no cycles yet
        if (firstCycleStart.isAfter(today)) {
            return 0;
        }

        LocalDate currentCycleStart = currentCycleStart(
                buildTemp(fromDate, billingDay, model));

        if (model == BillingModel.ADVANCE) {
            long months = ChronoUnit.MONTHS.between(
                    firstCycleStart, currentCycleStart) + 1;
            return Math.max(0, months);
        } else {
            // ARREARS — only completed cycles
            LocalDate currentCycleEnd = cycleEnd(currentCycleStart, billingDay);
            if (today.isBefore(currentCycleEnd) || today.isEqual(currentCycleEnd)) {
                long months = ChronoUnit.MONTHS.between(
                        firstCycleStart, currentCycleStart);
                return Math.max(0, months);
            } else {
                long months = ChronoUnit.MONTHS.between(
                        firstCycleStart, currentCycleStart) + 1;
                return Math.max(0, months);
            }
        }
    }

    // Helper to build a temp-like object for currentCycleStart
    private static RentalAgreement buildTemp(
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