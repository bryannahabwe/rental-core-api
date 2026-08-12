package com.cognix.rentalcoreapi.modules.reports.service;

import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.repository.RentalAgreementRepository;
import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.service.AuditWriter;
import com.cognix.rentalcoreapi.modules.payments.repository.PaymentRepository;
import com.cognix.rentalcoreapi.modules.reports.dto.MonthlyCollectionResponse;
import com.cognix.rentalcoreapi.modules.reports.dto.OccupancyReportResponse;
import com.cognix.rentalcoreapi.modules.reports.dto.PaymentReportResponse;
import com.cognix.rentalcoreapi.modules.reports.dto.SummaryResponse;
import com.cognix.rentalcoreapi.modules.tenants.repository.TenantRepository;
import com.cognix.rentalcoreapi.modules.units.repository.RentalUnitRepository;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import com.cognix.rentalcoreapi.shared.security.PropertyAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final RentalUnitRepository unitRepository;
    private final TenantRepository tenantRepository;
    private final RentalAgreementRepository agreementRepository;
    private final PaymentRepository paymentRepository;
    private final AuditWriter auditWriter;
    private final PropertyAccessGuard propertyAccessGuard;

    public SummaryResponse getSummary() {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        UUID propertyId = propertyAccessGuard.requireAccessibleProperty();

        long totalUnits = unitRepository.countByLandlordId(landlordId, propertyId);
        long occupiedUnits = unitRepository.countByLandlordIdAndIsAvailable(landlordId, false, propertyId);
        long availableUnits = unitRepository.countByLandlordIdAndIsAvailable(landlordId, true, propertyId);
        long totalTenants = tenantRepository.countByLandlordId(landlordId, propertyId);
        long activeAgreements = agreementRepository.countByLandlordIdAndStatus(
                landlordId, AgreementStatus.ACTIVE, propertyId);
        long terminatedAgreements = agreementRepository.countByLandlordIdAndStatus(
                landlordId, AgreementStatus.TERMINATED, propertyId);

        LocalDate epoch = LocalDate.of(2000, 1, 1);
        LocalDate today = LocalDate.now();
        BigDecimal totalRevenue = paymentRepository
                .sumAmountByLandlordIdAndDateRange(landlordId, propertyId, epoch, today)
                // Forfeited security deposits are landlord income too.
                .add(agreementRepository.sumForfeitedDepositByLandlordIdAndDateRange(
                        landlordId, propertyId, epoch, today));

        return new SummaryResponse(
                totalUnits,
                occupiedUnits,
                availableUnits,
                totalTenants,
                activeAgreements,
                terminatedAgreements,
                totalRevenue
        );
    }

    public PaymentReportResponse getPaymentReport(LocalDate from, LocalDate to) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        UUID propertyId = propertyAccessGuard.requireAccessibleProperty();

        if (from == null) {
            from = LocalDate.now().withDayOfMonth(1);
        }
        if (to == null) {
            to = LocalDate.now();
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "From date cannot be after to date");
        }

        long totalPayments = paymentRepository.countByLandlordIdAndPaymentDateBetween(
                landlordId, propertyId, from, to);

        BigDecimal totalAmount = paymentRepository.sumAmountByLandlordIdAndDateRange(
                landlordId, propertyId, from, to);

        // The one report read worth recording: it's the deliberate "pull the
        // figures for this period" action, and the row states what was
        // disclosed. The monthly-collection chart is the same data over the same
        // range, and /summary and /occupancy are dashboard tiles fetched on every
        // page load — logging those would bury the trail without adding a fact.
        auditWriter.record(AuditModule.REPORT, AuditAction.VIEW_REPORT, propertyId, null,
                "%s viewed the payment report for %s → %s (%d payments, %s).".formatted(
                        JwtUtils.getCurrentUserName(), from, to, totalPayments, totalAmount));

        return new PaymentReportResponse(from, to, totalPayments, totalAmount);
    }

    /**
     * Month-by-month collection totals for [from, to] — a single aggregate
     * (like getPaymentReport) isn't useful as a bar chart, there's nothing
     * to compare it against. Buckets are by paymentDate (cash actually
     * received in that calendar month), clamped to the requested range on
     * the first/last (possibly partial) month.
     */
    public List<MonthlyCollectionResponse> getMonthlyCollection(LocalDate from, LocalDate to) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        UUID propertyId = propertyAccessGuard.requireAccessibleProperty();

        if (from == null) {
            from = LocalDate.now().minusMonths(5).withDayOfMonth(1);
        }
        if (to == null) {
            to = LocalDate.now();
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "From date cannot be after to date");
        }

        List<MonthlyCollectionResponse> months = new ArrayList<>();
        LocalDate cursor = from.withDayOfMonth(1);

        while (!cursor.isAfter(to)) {
            LocalDate bucketStart = cursor.isBefore(from) ? from : cursor;
            LocalDate monthEnd = cursor.withDayOfMonth(cursor.lengthOfMonth());
            LocalDate bucketEnd = monthEnd.isAfter(to) ? to : monthEnd;

            long totalPayments = paymentRepository.countByLandlordIdAndPaymentDateBetween(
                    landlordId, propertyId, bucketStart, bucketEnd);
            BigDecimal totalAmount = paymentRepository.sumAmountByLandlordIdAndDateRange(
                    landlordId, propertyId, bucketStart, bucketEnd);

            String label = cursor.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                    + " " + cursor.getYear();

            months.add(new MonthlyCollectionResponse(label, cursor, totalPayments, totalAmount));

            cursor = cursor.plusMonths(1);
        }

        return months;
    }

    public OccupancyReportResponse getOccupancyReport() {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        UUID propertyId = propertyAccessGuard.requireAccessibleProperty();

        long totalUnits = unitRepository.countByLandlordId(landlordId, propertyId);
        long occupiedUnits = unitRepository.countByLandlordIdAndIsAvailable(landlordId, false, propertyId);
        long availableUnits = unitRepository.countByLandlordIdAndIsAvailable(landlordId, true, propertyId);

        BigDecimal occupancyRate = totalUnits > 0
                ? BigDecimal.valueOf(occupiedUnits)
                  .divide(BigDecimal.valueOf(totalUnits), 4, RoundingMode.HALF_UP)
                  .multiply(BigDecimal.valueOf(100))
                  .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new OccupancyReportResponse(
                totalUnits,
                occupiedUnits,
                availableUnits,
                occupancyRate
        );
    }
}