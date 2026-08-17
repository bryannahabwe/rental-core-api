package com.cognix.rentalcoreapi.modules.agreements.service;

import com.cognix.rentalcoreapi.modules.agreements.dto.CycleStatusResponse;
import com.cognix.rentalcoreapi.modules.agreements.dto.MoveOutRequest;
import com.cognix.rentalcoreapi.modules.agreements.dto.RentalAgreementRequest;
import com.cognix.rentalcoreapi.modules.agreements.dto.RentalAgreementResponse;
import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.model.BillingModel;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import com.cognix.rentalcoreapi.modules.agreements.model.TenantType;
import com.cognix.rentalcoreapi.modules.agreements.repository.RentalAgreementRepository;
import com.cognix.rentalcoreapi.modules.audit.AuditDiff;
import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.service.AuditWriter;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.income.service.OtherIncomeService;
import com.cognix.rentalcoreapi.modules.payments.repository.PaymentRepository;
import com.cognix.rentalcoreapi.modules.tenants.repository.TenantRepository;
import com.cognix.rentalcoreapi.modules.units.repository.RentalUnitRepository;
import com.cognix.rentalcoreapi.shared.exception.ConflictException;
import com.cognix.rentalcoreapi.shared.exception.NotFoundException;
import com.cognix.rentalcoreapi.shared.response.PagedResponse;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import com.cognix.rentalcoreapi.shared.security.PropertyAccessGuard;
import com.cognix.rentalcoreapi.shared.util.BillingCycleUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RentalAgreementService {

    private final RentalAgreementRepository agreementRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RentalUnitRepository unitRepository;
    private final PaymentRepository paymentRepository;
    private final PropertyAccessGuard propertyAccessGuard;
    private final AuditWriter auditWriter;
    private final AgreementBalanceCalculator balanceCalculator;
    private final OtherIncomeService otherIncomeService;

    public PagedResponse<RentalAgreementResponse> getAllAgreements(
            Pageable pageable, String search, AgreementStatus status) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        UUID propertyId = propertyAccessGuard.requireAccessibleProperty();

        Page<RentalAgreement> page;

        if (status != null) {
            page = agreementRepository.findAllByLandlordIdWithStatusAndSearch(
                    landlordId, status, propertyId, search, pageable);
        } else {
            page = agreementRepository.findAllByLandlordIdWithSearch(
                    landlordId, propertyId, search, pageable);
        }

        return PagedResponse.from(page.map(RentalAgreementResponse::from));
    }

    public RentalAgreementResponse getAgreement(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        RentalAgreement agreement = agreementRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new NotFoundException("Agreement not found"));
        propertyAccessGuard.assertCanAccess(agreement.getProperty().getId());
        return RentalAgreementResponse.from(agreement);
    }

    @Transactional
    public RentalAgreementResponse createAgreement(RentalAgreementRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        var unit = unitRepository.findByIdAndLandlordId(request.unitId(), landlordId)
                .orElseThrow(() -> new NotFoundException("Unit not found"));
        propertyAccessGuard.assertCanAccess(unit.getProperty().getId());

        var tenant = tenantRepository.findByIdAndLandlordId(request.tenantId(), landlordId)
                .orElseThrow(() -> new NotFoundException("Tenant not found"));

        // Tenant and unit must live in the same property — an agreement's
        // property is derived from the unit, so a cross-property pairing would
        // otherwise silently file the tenant's payments under the wrong book.
        if (tenant.getProperty() != null && unit.getProperty() != null
                && !tenant.getProperty().getId().equals(unit.getProperty().getId())) {
            throw new IllegalArgumentException(
                    "Tenant and unit belong to different properties");
        }

        if (agreementRepository.existsByUnitIdAndStatus(request.unitId(), AgreementStatus.ACTIVE)) {
            throw new ConflictException(
                    "Unit " + unit.getRoomNumber() + " already has an active agreement");
        }

        BigDecimal agreedRent = request.rentAmount() != null
                ? request.rentAmount()
                : unit.getRentAmount();

        // Determine tenant type — default to NEW if not provided
        TenantType tenantType = request.tenantType() != null
                ? request.tenantType()
                : TenantType.NEW;

        // Opening balance only meaningful for EXISTING tenants
        BigDecimal openingBalance = BigDecimal.ZERO;
        if (tenantType == TenantType.EXISTING && request.openingBalance() != null) {
            openingBalance = request.openingBalance();
        }

        // Determine billing day from startDate
        int billingDay = 1;
        if (request.startDate() != null) {
            billingDay = Math.min(request.startDate().getDayOfMonth(), 28);
        }

// Billing model — default ADVANCE
        BillingModel billingModel = request.billingModel() != null
                ? request.billingModel()
                : BillingModel.ADVANCE;

        RentalAgreement agreement = RentalAgreement.builder()
                .landlord(userRepository.getReferenceById(landlordId))
                .property(unit.getProperty())
                .tenant(tenant)
                .unit(unit)
                .startDate(request.startDate())
                .rentAmount(agreedRent)
                .depositAmount(request.depositAmount())
                .status(AgreementStatus.ACTIVE)
                .tenantType(tenantType)
                .openingBalance(openingBalance)
                .billingDay(billingDay)
                .billingModel(billingModel)
                .build();
        unit.setAvailable(false);
        unitRepository.save(unit);

        // saveAndFlush (not save) so @CreationTimestamp's INSERT-time
        // population actually happens before this response is built.
        RentalAgreement saved = agreementRepository.saveAndFlush(agreement);

        auditWriter.record(AuditModule.RENTAL_AGREEMENT, AuditAction.CREATE,
                unit.getProperty().getId(), tenant.getName(),
                "%s created an agreement for %s in unit %s.".formatted(
                        JwtUtils.getCurrentUserName(), tenant.getName(), unit.getRoomNumber()));

        return RentalAgreementResponse.from(saved);
    }

    @Transactional
    public RentalAgreementResponse recordMoveOut(UUID id, MoveOutRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        RentalAgreement agreement = agreementRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new NotFoundException("Agreement not found"));
        propertyAccessGuard.assertCanAccess(agreement.getProperty().getId());

        if (agreement.getStatus() == AgreementStatus.TERMINATED) {
            throw new ConflictException("Agreement is already terminated");
        }

        if (agreement.getStartDate() != null
                && request.moveOutDate().isBefore(agreement.getStartDate())) {
            throw new IllegalArgumentException(
                    "Move out date cannot be before the agreement start date");
        }

        // ── Security-deposit settlement (optional) ───────────────────────────
        // Validate against the balance BEFORE any mutation, so "outstanding"
        // matches what the move-out screen showed the user.
        BigDecimal deposit = nz(agreement.getDepositAmount());
        BigDecimal applied = nz(request.depositApplied());
        BigDecimal refunded = nz(request.depositRefunded());
        BigDecimal forfeited = nz(request.depositForfeited());
        boolean settling = applied.signum() > 0 || refunded.signum() > 0 || forfeited.signum() > 0;

        if (settling) {
            if (deposit.signum() == 0) {
                throw new IllegalArgumentException(
                        "No deposit is held on this agreement to settle");
            }
            if (applied.add(refunded).add(forfeited).compareTo(deposit) != 0) {
                throw new IllegalArgumentException(
                        "Applied, refunded and forfeited amounts must add up to the held deposit of "
                                + deposit.toPlainString());
            }
            BigDecimal outstanding = balanceCalculator
                    .summarize(agreement, computeCycleStatuses(agreement))
                    .outstanding();
            if (applied.compareTo(outstanding) > 0) {
                throw new IllegalArgumentException(
                        "Cannot apply more than the outstanding balance of "
                                + outstanding.toPlainString());
            }

            // Applying to the balance = adding a credit to openingBalance, the
            // same knob PaymentService uses to pay down arrears. The computed
            // balance shrinks automatically; no payment row is created, so the
            // applied deposit is (correctly) not counted as rental revenue.
            if (applied.signum() > 0) {
                agreement.setOpeningBalance(agreement.getOpeningBalance().add(applied));
            }
            agreement.setDepositApplied(applied);
            agreement.setDepositRefunded(refunded);
            agreement.setDepositForfeited(forfeited);
        }

        agreement.setMoveOutDate(request.moveOutDate());
        agreement.setStatus(AgreementStatus.TERMINATED);

        var unit = agreement.getUnit();
        unit.setAvailable(true);
        unitRepository.save(unit);

        RentalAgreement saved = agreementRepository.save(agreement);

        auditWriter.record(AuditModule.RENTAL_AGREEMENT, AuditAction.MOVE_OUT,
                agreement.getProperty().getId(), agreement.getTenant().getName(),
                "%s recorded move-out for %s from unit %s.".formatted(
                        JwtUtils.getCurrentUserName(), agreement.getTenant().getName(),
                        unit.getRoomNumber()));

        if (settling) {
            auditWriter.record(AuditModule.RENTAL_AGREEMENT, AuditAction.SETTLE_DEPOSIT,
                    agreement.getProperty().getId(), agreement.getTenant().getName(),
                    "%s settled the deposit for %s: applied %s, refunded %s, forfeited %s.".formatted(
                            JwtUtils.getCurrentUserName(), agreement.getTenant().getName(),
                            applied.toPlainString(), refunded.toPlainString(),
                            forfeited.toPlainString()));

            // A forfeited deposit is landlord income. Record it as a first-class
            // income row so it shows in the income ledger and is counted once by
            // the reports (which now sum other_income rather than deriving the
            // forfeited amount from the agreement columns).
            if (forfeited.signum() > 0) {
                otherIncomeService.recordForfeitedDeposit(
                        landlordId, saved, forfeited, request.moveOutDate());
            }
        }

        return RentalAgreementResponse.from(saved);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    @Transactional
    public RentalAgreementResponse updateAgreement(UUID id, RentalAgreementRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        RentalAgreement agreement = agreementRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new NotFoundException("Agreement not found"));
        propertyAccessGuard.assertCanAccess(agreement.getProperty().getId());

        var oldRent = agreement.getRentAmount();
        var oldDeposit = agreement.getDepositAmount();
        var oldBilling = agreement.getBillingModel();
        var oldOpening = agreement.getOpeningBalance();
        var oldStart = agreement.getStartDate();

        // Update allowed fields
        if (request.rentAmount() != null) {
            agreement.setRentAmount(request.rentAmount());
        }
        if (request.depositAmount() != null) {
            agreement.setDepositAmount(request.depositAmount());
        }
        if (request.billingModel() != null) {
            agreement.setBillingModel(request.billingModel());
        }
        if (request.openingBalance() != null) {
            agreement.setOpeningBalance(request.openingBalance());
        }
        if (request.startDate() != null) {
            agreement.setStartDate(request.startDate());
            agreement.setBillingDay(
                    Math.min(request.startDate().getDayOfMonth(), 28));
        }

        List<String> changes = new ArrayList<>();
        AuditDiff.diff(changes, "rent", oldRent, agreement.getRentAmount());
        AuditDiff.diff(changes, "deposit", oldDeposit, agreement.getDepositAmount());
        AuditDiff.diff(changes, "billing model", oldBilling, agreement.getBillingModel());
        AuditDiff.diff(changes, "opening balance", oldOpening, agreement.getOpeningBalance());
        AuditDiff.diff(changes, "start date", oldStart, agreement.getStartDate());

        RentalAgreement saved = agreementRepository.save(agreement);

        if (!changes.isEmpty()) {
            auditWriter.record(AuditModule.RENTAL_AGREEMENT, AuditAction.UPDATE,
                    saved.getProperty().getId(), saved.getTenant().getName(),
                    "%s updated the agreement for %s: %s.".formatted(
                            JwtUtils.getCurrentUserName(), saved.getTenant().getName(),
                            String.join("; ", changes)));
        }

        return RentalAgreementResponse.from(saved);
    }

    public List<CycleStatusResponse> getCycleStatuses(UUID agreementId) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        RentalAgreement agreement = agreementRepository
                .findByIdAndLandlordId(agreementId, landlordId)
                .orElseThrow(() -> new NotFoundException("Agreement not found"));
        propertyAccessGuard.assertCanAccess(agreement.getProperty().getId());

        return computeCycleStatuses(agreement);
    }

    /**
     * Shared cycle-by-cycle breakdown, also used by TenantService to build
     * the tenant ledger — single source of truth for per-cycle expected/paid,
     * so the ledger table and the cycle picker never disagree.
     */
    public List<CycleStatusResponse> computeCycleStatuses(RentalAgreement agreement) {
        List<CycleStatusResponse> cycles = new ArrayList<>();

        LocalDate cycleStart = BillingCycleUtils.effectiveStartDate(agreement);

        // currentCycleStart() only reads startDate/billingDay/billingModel off
        // the agreement — substitute a temp one when startDate itself is null
        // so this falls back to createdAt exactly like cyclesElapsed() does,
        // instead of silently returning no cycles at all for such agreements.
        RentalAgreement cycleAgreement = agreement.getStartDate() != null
                ? agreement
                : BillingCycleUtils.buildTemp(
                        cycleStart, agreement.getBillingDay(), agreement.getBillingModel());

        // Current due cycle
        LocalDate lastCycleStart = BillingCycleUtils.currentCycleStart(cycleAgreement);

        // If no cycle is due yet (ARREARS tenant, first cycle not completes)
        // still show the current in-progress cycle so landlord can record early payment
        if (lastCycleStart == null) {
            lastCycleStart = cycleStart;
        }

        // Always include ONE future cycle beyond the current due cycle
        // so tenants who pay early can pay ahead
        LocalDate futureCycleStart = BillingCycleUtils.nextCycleStart(
                lastCycleStart, agreement.getBillingDay());

        // Generate all cycles from startDate up to and including future cycle
        while (!cycleStart.isAfter(futureCycleStart)) {
            LocalDate cycleEnd = BillingCycleUtils.cycleEnd(
                    cycleStart, agreement.getBillingDay());

            // Retained, not gross: a cycle that received a 300k payment
            // against 180k rent keeps 180k and rolls 120k forward, so its
            // Paid/Balance must read 180k/0 — not 300k with a 120k credit
            // that's already been re-recorded as the next cycle's rollover.
            BigDecimal paidAmount = paymentRepository.sumRetainedByAgreementAndCycle(
                    agreement.getId(), cycleStart, cycleEnd);

            BigDecimal expectedAmount = agreement.getRentAmount();

            String status;
            if (paidAmount.compareTo(expectedAmount) >= 0) {
                status = "PAID";
            } else if (paidAmount.compareTo(BigDecimal.ZERO) > 0) {
                status = "PARTIAL";
            } else {
                status = "UNPAID";
            }

            boolean due = BillingCycleUtils.isDue(
                    agreement.getBillingModel(), cycleStart, cycleEnd, LocalDate.now());

            cycles.add(new CycleStatusResponse(
                    cycleStart,
                    cycleEnd,
                    expectedAmount,
                    paidAmount,
                    status,
                    due
            ));

            // Advance to next cycle
            cycleStart = BillingCycleUtils.nextCycleStart(
                    cycleStart, agreement.getBillingDay());
        }

        return cycles;
    }
}