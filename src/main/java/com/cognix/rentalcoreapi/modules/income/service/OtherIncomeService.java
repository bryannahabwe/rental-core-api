package com.cognix.rentalcoreapi.modules.income.service;

import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.service.AuditWriter;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.income.dto.OtherIncomeRequest;
import com.cognix.rentalcoreapi.modules.income.dto.OtherIncomeResponse;
import com.cognix.rentalcoreapi.modules.income.model.OtherIncome;
import com.cognix.rentalcoreapi.modules.income.repository.OtherIncomeRepository;
import com.cognix.rentalcoreapi.modules.paymentmethods.service.PaymentMethodService;
import com.cognix.rentalcoreapi.modules.properties.model.Property;
import com.cognix.rentalcoreapi.modules.properties.repository.PropertyRepository;
import com.cognix.rentalcoreapi.modules.tenants.model.Tenant;
import com.cognix.rentalcoreapi.modules.tenants.repository.TenantRepository;
import com.cognix.rentalcoreapi.shared.exception.NotFoundException;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import com.cognix.rentalcoreapi.shared.security.PropertyAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OtherIncomeService {

    /** Category used for the auto-generated row when a deposit is forfeited. */
    public static final String DEPOSIT_FORFEITURE_CATEGORY = "Deposit forfeiture";

    /**
     * Method for auto-generated rows. Matches the name seeded by
     * {@code PaymentMethodService.DEFAULT_METHODS}, so it resolves against the
     * managed list without creating a duplicate option.
     */
    private static final String CASH_METHOD = "Cash";

    private final OtherIncomeRepository otherIncomeRepository;
    private final PropertyRepository propertyRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PaymentMethodService paymentMethodService;
    private final PropertyAccessGuard propertyAccessGuard;
    private final AuditWriter auditWriter;

    public OtherIncomeResponse getOtherIncome(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        OtherIncome income = otherIncomeRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new NotFoundException("Income entry not found"));
        propertyAccessGuard.assertCanAccess(income.getProperty().getId());
        return OtherIncomeResponse.from(income);
    }

    @Transactional
    public OtherIncomeResponse recordOtherIncome(OtherIncomeRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        Property property = propertyRepository
                .findByIdAndLandlordId(request.propertyId(), landlordId)
                .orElseThrow(() -> new NotFoundException("Property not found"));
        propertyAccessGuard.assertCanAccess(property.getId());

        Tenant tenant = resolveTenant(request.tenantId(), landlordId);

        OtherIncome income = OtherIncome.builder()
                .landlord(userRepository.getReferenceById(landlordId))
                .property(property)
                .tenant(tenant)
                .incomeDate(request.incomeDate())
                .amount(request.amount())
                .category(request.category().trim())
                .method(paymentMethodService.resolveOrCreate(landlordId, request.method()))
                .receivedBy(trimToNull(request.receivedBy()))
                .reference(request.reference())
                .notes(request.notes())
                .build();

        OtherIncome saved = otherIncomeRepository.saveAndFlush(income);

        auditWriter.record(AuditModule.INCOME, AuditAction.RECORD_INCOME,
                property.getId(), saved.getId().toString(),
                "%s recorded UGX %,.0f of %s income for %s.".formatted(
                        JwtUtils.getCurrentUserName(), request.amount(),
                        income.getCategory(), property.getName()));

        return OtherIncomeResponse.from(saved);
    }

    @Transactional
    public OtherIncomeResponse updateOtherIncome(UUID id, OtherIncomeRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        OtherIncome income = otherIncomeRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new NotFoundException("Income entry not found"));
        propertyAccessGuard.assertCanAccess(income.getProperty().getId());

        Property property = propertyRepository
                .findByIdAndLandlordId(request.propertyId(), landlordId)
                .orElseThrow(() -> new NotFoundException("Property not found"));
        propertyAccessGuard.assertCanAccess(property.getId());

        income.setProperty(property);
        income.setTenant(resolveTenant(request.tenantId(), landlordId));
        income.setIncomeDate(request.incomeDate());
        income.setAmount(request.amount());
        income.setCategory(request.category().trim());
        income.setMethod(paymentMethodService.resolveOrCreate(landlordId, request.method()));
        income.setReceivedBy(trimToNull(request.receivedBy()));
        income.setReference(request.reference());
        income.setNotes(request.notes());

        OtherIncome saved = otherIncomeRepository.saveAndFlush(income);

        auditWriter.record(AuditModule.INCOME, AuditAction.UPDATE,
                property.getId(), saved.getId().toString(),
                "%s updated a %s income entry for %s.".formatted(
                        JwtUtils.getCurrentUserName(), income.getCategory(),
                        property.getName()));

        return OtherIncomeResponse.from(saved);
    }

    @Transactional
    public void deleteOtherIncome(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        OtherIncome income = otherIncomeRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new NotFoundException("Income entry not found"));
        propertyAccessGuard.assertCanAccess(income.getProperty().getId());

        otherIncomeRepository.delete(income);

        auditWriter.record(AuditModule.INCOME, AuditAction.DELETE,
                income.getProperty().getId(), id.toString(),
                "%s deleted a %s income entry for %s.".formatted(
                        JwtUtils.getCurrentUserName(), income.getCategory(),
                        income.getProperty().getName()));
    }

    /**
     * Records a forfeited security deposit as income at move-out. Called from
     * {@code RentalAgreementService.recordMoveOut} within its transaction, so
     * this row commits/rolls back with the move-out. Property access has
     * already been asserted by the caller.
     */
    @Transactional
    public void recordForfeitedDeposit(UUID landlordId, RentalAgreement agreement,
                                       BigDecimal amount, LocalDate date) {
        OtherIncome income = OtherIncome.builder()
                .landlord(userRepository.getReferenceById(landlordId))
                .property(agreement.getProperty())
                .tenant(agreement.getTenant())
                .agreement(agreement)
                .incomeDate(date)
                .amount(amount)
                .category(DEPOSIT_FORFEITURE_CATEGORY)
                .method(paymentMethodService.resolveOrCreate(landlordId, CASH_METHOD))
                .reference("Forfeited deposit at move-out")
                .build();
        otherIncomeRepository.save(income);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Tenant resolveTenant(UUID tenantId, UUID landlordId) {
        if (tenantId == null) {
            return null;
        }
        return tenantRepository.findByIdAndLandlordId(tenantId, landlordId)
                .orElseThrow(() -> new NotFoundException("Tenant not found"));
    }
}
