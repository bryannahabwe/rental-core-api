package com.cognix.rentalcoreapi.shared.util;

import com.cognix.rentalcoreapi.modules.agreements.model.BillingModel;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the cycle date-math. The deterministic, argument-taking
 * methods (cycleEnd / nextCycleStart / effectiveStartDate) are exercised
 * exhaustively around the month-end clamping edges; the now()-dependent methods
 * are covered only where the result is stable regardless of today's date.
 */
class BillingCycleUtilsTest {

    // ── cycleEnd: [cycleStart, cycleStart + 1 month) minus a day, clamped ──

    @Test
    void cycleEnd_isDayBeforeSameDayNextMonth() {
        // Apr 15 → cycle runs to May 14.
        assertThat(BillingCycleUtils.cycleEnd(LocalDate.of(2026, 4, 15), 15))
                .isEqualTo(LocalDate.of(2026, 5, 14));
    }

    @Test
    void cycleEnd_clampsWhenNextMonthIsShorter() {
        // billingDay 31, Jan 31 → next month Feb has no 31st; clamp to Feb 28,
        // so the cycle ends the day before: Feb 27 (2026 is not a leap year).
        assertThat(BillingCycleUtils.cycleEnd(LocalDate.of(2026, 1, 31), 31))
                .isEqualTo(LocalDate.of(2026, 2, 27));
    }

    @Test
    void cycleEnd_clampsIntoLeapFebruary() {
        // billingDay 31, Jan 31 2024 → Feb 2024 has 29 days; clamp to Feb 29,
        // cycle ends Feb 28.
        assertThat(BillingCycleUtils.cycleEnd(LocalDate.of(2024, 1, 31), 31))
                .isEqualTo(LocalDate.of(2024, 2, 28));
    }

    @Test
    void cycleEnd_clampsThirtyDayMonth() {
        // billingDay 31, Mar 31 → April has 30 days; clamp to Apr 30, ends Apr 29.
        assertThat(BillingCycleUtils.cycleEnd(LocalDate.of(2026, 3, 31), 31))
                .isEqualTo(LocalDate.of(2026, 4, 29));
    }

    // ── nextCycleStart: same-day next month, clamped to month length ──

    @Test
    void nextCycleStart_normal() {
        assertThat(BillingCycleUtils.nextCycleStart(LocalDate.of(2026, 4, 15), 15))
                .isEqualTo(LocalDate.of(2026, 5, 15));
    }

    @Test
    void nextCycleStart_clampsShortMonth() {
        // billingDay 31, Jan 31 → next start clamps to Feb 28 (non-leap).
        assertThat(BillingCycleUtils.nextCycleStart(LocalDate.of(2026, 1, 31), 31))
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void nextCycleStart_clampsLeapFebruary() {
        assertThat(BillingCycleUtils.nextCycleStart(LocalDate.of(2024, 1, 31), 31))
                .isEqualTo(LocalDate.of(2024, 2, 29));
    }

    // ── effectiveStartDate: explicit startDate, else createdAt ──

    @Test
    void effectiveStartDate_prefersExplicitStartDate() {
        RentalAgreement agreement = new RentalAgreement();
        agreement.setStartDate(LocalDate.of(2026, 2, 10));
        agreement.setCreatedAt(LocalDate.of(2026, 1, 1).atStartOfDay());

        assertThat(BillingCycleUtils.effectiveStartDate(agreement))
                .isEqualTo(LocalDate.of(2026, 2, 10));
    }

    @Test
    void effectiveStartDate_fallsBackToCreatedAt() {
        RentalAgreement agreement = new RentalAgreement();
        agreement.setStartDate(null);
        agreement.setCreatedAt(LocalDate.of(2026, 1, 5).atStartOfDay());

        assertThat(BillingCycleUtils.effectiveStartDate(agreement))
                .isEqualTo(LocalDate.of(2026, 1, 5));
    }

    // ── currentCycleStart: date-stable cases ──

    @Test
    void currentCycleStart_nullWhenNoStartDate() {
        RentalAgreement agreement = new RentalAgreement();
        agreement.setStartDate(null);
        agreement.setBillingDay(1);
        agreement.setBillingModel(BillingModel.ADVANCE);

        assertThat(BillingCycleUtils.currentCycleStart(agreement)).isNull();
    }

    @Test
    void currentCycleStart_arrearsFirstCycleNotYetEnded_isNull() {
        // Start today on an ARREARS agreement: the first cycle cannot have
        // completed yet, so nothing is due — regardless of the actual date.
        LocalDate today = LocalDate.now();
        RentalAgreement agreement = new RentalAgreement();
        agreement.setStartDate(today);
        agreement.setBillingDay(today.getDayOfMonth());
        agreement.setBillingModel(BillingModel.ARREARS);

        assertThat(BillingCycleUtils.currentCycleStart(agreement)).isNull();
    }

    @Test
    void currentCycleStart_advanceCountsFromToday() {
        // Start today on an ADVANCE agreement: the current cycle is due
        // immediately and begins today.
        LocalDate today = LocalDate.now();
        RentalAgreement agreement = new RentalAgreement();
        agreement.setStartDate(today);
        agreement.setBillingDay(today.getDayOfMonth());
        agreement.setBillingModel(BillingModel.ADVANCE);

        assertThat(BillingCycleUtils.currentCycleStart(agreement)).isEqualTo(today);
    }
}
