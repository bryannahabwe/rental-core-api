package com.cognix.rentalcoreapi.modules.reports.service;

import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.repository.RentalAgreementRepository;
import com.cognix.rentalcoreapi.modules.payments.repository.PaymentRepository;
import com.cognix.rentalcoreapi.modules.reports.dto.OccupancyReportResponse;
import com.cognix.rentalcoreapi.modules.reports.dto.PaymentReportResponse;
import com.cognix.rentalcoreapi.modules.reports.dto.SummaryResponse;
import com.cognix.rentalcoreapi.modules.tenants.repository.TenantRepository;
import com.cognix.rentalcoreapi.modules.units.repository.RentalUnitRepository;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final RentalUnitRepository unitRepository;
    private final TenantRepository tenantRepository;
    private final RentalAgreementRepository agreementRepository;
    private final PaymentRepository paymentRepository;

    public SummaryResponse getSummary() {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        long totalUnits        = unitRepository.countByLandlordId(landlordId);
        long occupiedUnits     = unitRepository.countByLandlordIdAndIsAvailable(landlordId, false);
        long availableUnits    = unitRepository.countByLandlordIdAndIsAvailable(landlordId, true);
        long totalTenants      = tenantRepository.countByLandlordId(landlordId);
        long activeAgreements  = agreementRepository.countByLandlordIdAndStatus(
                landlordId, AgreementStatus.ACTIVE);
        long terminatedAgreements = agreementRepository.countByLandlordIdAndStatus(
                landlordId, AgreementStatus.TERMINATED);

        BigDecimal totalRevenue = paymentRepository.sumAmountByLandlordIdAndDateRange(
                landlordId,
                LocalDate.of(2000, 1, 1),
                LocalDate.now()
        );

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
                landlordId, from, to);

        BigDecimal totalAmount = paymentRepository.sumAmountByLandlordIdAndDateRange(
                landlordId, from, to);

        return new PaymentReportResponse(from, to, totalPayments, totalAmount);
    }

    public OccupancyReportResponse getOccupancyReport() {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        long totalUnits     = unitRepository.countByLandlordId(landlordId);
        long occupiedUnits  = unitRepository.countByLandlordIdAndIsAvailable(landlordId, false);
        long availableUnits = unitRepository.countByLandlordIdAndIsAvailable(landlordId, true);

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