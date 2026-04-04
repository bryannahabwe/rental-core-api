package com.cognix.rentalcoreapi.modules.tenants.service;

import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import com.cognix.rentalcoreapi.modules.agreements.repository.RentalAgreementRepository;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.payments.repository.PaymentRepository;
import com.cognix.rentalcoreapi.modules.tenants.dto.TenantRequest;
import com.cognix.rentalcoreapi.modules.tenants.dto.TenantResponse;
import com.cognix.rentalcoreapi.modules.tenants.model.Tenant;
import com.cognix.rentalcoreapi.modules.tenants.repository.TenantRepository;
import com.cognix.rentalcoreapi.shared.response.PagedResponse;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RentalAgreementRepository agreementRepository;
    private final PaymentRepository paymentRepository;

    public PagedResponse<TenantResponse> getAllTenants(Pageable pageable, String search) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        int currentMonth = LocalDate.now().getMonthValue();
        int currentYear = LocalDate.now().getYear();

        Page<TenantResponse> responses = tenantRepository
                .findAllByLandlordIdWithSearch(landlordId, search, pageable)
                .map(tenant -> enrichWithBalance(tenant, landlordId, currentMonth, currentYear));

        return PagedResponse.from(responses);
    }

    public TenantResponse getTenant(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        int currentMonth = LocalDate.now().getMonthValue();
        int currentYear = LocalDate.now().getYear();

        Tenant tenant = tenantRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

        return enrichWithBalance(tenant, landlordId, currentMonth, currentYear);
    }

    public TenantResponse createTenant(TenantRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        if (tenantRepository.existsByPhoneAndLandlordId(request.phone(), landlordId)) {
            throw new IllegalArgumentException(
                    "Tenant with phone number already exists: " + request.phone());
        }

        if (request.email() != null &&
                tenantRepository.existsByEmailAndLandlordId(request.email(), landlordId)) {
            throw new IllegalArgumentException(
                    "Tenant with email already exists: " + request.email());
        }

        Tenant tenant = Tenant.builder()
                .landlord(userRepository.getReferenceById(landlordId))
                .name(request.name())
                .phone(request.phone())
                .email(request.email())
                .address(request.address())
                .build();

        return TenantResponse.from(tenantRepository.save(tenant));
    }

    public TenantResponse updateTenant(UUID id, TenantRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        Tenant tenant = tenantRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

        if (!tenant.getPhone().equals(request.phone()) &&
                tenantRepository.existsByPhoneAndLandlordId(request.phone(), landlordId)) {
            throw new IllegalArgumentException(
                    "Tenant with phone number already exists: " + request.phone());
        }

        if (request.email() != null &&
                !request.email().equals(tenant.getEmail()) &&
                tenantRepository.existsByEmailAndLandlordId(request.email(), landlordId)) {
            throw new IllegalArgumentException(
                    "Tenant with email already exists: " + request.email());
        }

        tenant.setName(request.name());
        tenant.setPhone(request.phone());
        tenant.setEmail(request.email());
        tenant.setAddress(request.address());

        return TenantResponse.from(tenantRepository.save(tenant));
    }

    public void deleteTenant(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        Tenant tenant = tenantRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

        tenantRepository.delete(tenant);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private TenantResponse enrichWithBalance(
            Tenant tenant, UUID landlordId,
            int currentMonth, int currentYear) {

        Optional<RentalAgreement> activeAgreement = agreementRepository
                .findFirstByTenantIdAndLandlordIdAndStatus(
                        tenant.getId(), landlordId, AgreementStatus.ACTIVE);

        if (activeAgreement.isEmpty()) {
            return TenantResponse.from(tenant);
        }

        RentalAgreement agreement = activeAgreement.get();

        // How many months has this tenant been active?
        // Count from startDate (or agreement createdAt) to now
        LocalDate from = agreement.getStartDate() != null
                ? agreement.getStartDate()
                : agreement.getCreatedAt().toLocalDate();

        LocalDate now = LocalDate.now();

        // Total months elapsed (inclusive of current month)
        long totalMonths = ChronoUnit.MONTHS.between(
                from.withDayOfMonth(1),
                now.withDayOfMonth(1)
        ) + 1;

        // Total ever owed = rent * months + arrears from opening balance
        BigDecimal totalEverOwed = agreement.getRentAmount()
                .multiply(BigDecimal.valueOf(totalMonths));

        // Add opening arrears (negative opening balance)
        BigDecimal openingArrears = agreement.getOpeningBalance()
                .min(BigDecimal.ZERO).abs();
        totalEverOwed = totalEverOwed.add(openingArrears);

        // Subtract opening credit (positive opening balance)
        BigDecimal openingCredit = agreement.getOpeningBalance()
                .max(BigDecimal.ZERO);
        totalEverOwed = totalEverOwed.subtract(openingCredit);

        // Total ever paid = sum of ALL payments for this agreement
        BigDecimal totalEverPaid = paymentRepository
                .sumAllByAgreement(agreement.getId());

        // Outstanding = ever owed - ever paid
        BigDecimal outstanding = totalEverOwed.subtract(totalEverPaid)
                .max(BigDecimal.ZERO);

        // Status — based on current month only for display
        BigDecimal currentMonthPaid = paymentRepository.sumByAgreementAndPeriod(
                agreement.getId(), currentMonth, currentYear);

        String periodStatus;
        if (outstanding.compareTo(BigDecimal.ZERO) == 0) {
            periodStatus = "PAID";
        } else if (currentMonthPaid.compareTo(BigDecimal.ZERO) > 0) {
            periodStatus = "PARTIAL";
        } else {
            periodStatus = "UNPAID";
        }

        return TenantResponse.from(tenant).withBalance(
                agreement.getUnit().getRoomNumber(),
                agreement.getRentAmount(),
                outstanding,
                periodStatus,
                currentMonth,
                currentYear
        );
    }
}