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

        // Sum all payments for current period
        BigDecimal totalPaid = paymentRepository.sumByAgreementAndPeriod(
                agreement.getId(), currentMonth, currentYear);

        BigDecimal openingBalance = agreement.getOpeningBalance();

        // Opening balance logic:
        // Positive = credit (reduces outstanding)
        // Negative = arrears (increases outstanding)
        BigDecimal openingCredit  = openingBalance.max(BigDecimal.ZERO);   // positive part
        BigDecimal openingArrears = openingBalance.min(BigDecimal.ZERO).abs(); // negative part as positive

        // outstanding = rent - paid - credit + arrears
        BigDecimal outstanding = agreement.getRentAmount()
                .subtract(totalPaid)
                .subtract(openingCredit)
                .add(openingArrears);

        // Compute status
        String periodStatus;
        if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            periodStatus = "PAID";
        } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            periodStatus = "PARTIAL";
        } else {
            periodStatus = "UNPAID";
        }

        return TenantResponse.from(tenant).withBalance(
                agreement.getUnit().getRoomNumber(),
                agreement.getRentAmount(),
                outstanding.max(BigDecimal.ZERO),
                periodStatus,
                currentMonth,
                currentYear
        );
    }
}