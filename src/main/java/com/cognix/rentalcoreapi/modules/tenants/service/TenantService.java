package com.cognix.rentalcoreapi.modules.tenants.service;

import com.cognix.rentalcoreapi.modules.audit.AuditDiff;
import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.service.AuditWriter;
import com.cognix.rentalcoreapi.modules.agreements.dto.CycleStatusResponse;
import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.model.BillingModel;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import com.cognix.rentalcoreapi.modules.agreements.repository.RentalAgreementRepository;
import com.cognix.rentalcoreapi.modules.agreements.service.RentalAgreementService;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.payments.dto.PaymentResponse;
import com.cognix.rentalcoreapi.modules.payments.model.Payment;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentSource;
import com.cognix.rentalcoreapi.modules.payments.repository.PaymentRepository;
import com.cognix.rentalcoreapi.modules.properties.model.Property;
import com.cognix.rentalcoreapi.modules.properties.repository.PropertyRepository;
import com.cognix.rentalcoreapi.modules.tenants.dto.TenantLedgerResponse;
import com.cognix.rentalcoreapi.modules.tenants.dto.TenantRequest;
import com.cognix.rentalcoreapi.modules.tenants.dto.TenantResponse;
import com.cognix.rentalcoreapi.modules.tenants.model.Tenant;
import com.cognix.rentalcoreapi.modules.tenants.repository.TenantRepository;
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
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RentalAgreementRepository agreementRepository;
    private final PaymentRepository paymentRepository;
    private final RentalAgreementService agreementService;
    private final PropertyRepository propertyRepository;
    private final PropertyAccessGuard propertyAccessGuard;
    private final AuditWriter auditWriter;

    public PagedResponse<TenantResponse> getAllTenants(Pageable pageable, String search) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        UUID propertyId = propertyAccessGuard.requireAccessibleProperty();

        Page<TenantResponse> responses = tenantRepository
                .findAllByLandlordIdWithSearch(landlordId, propertyId, search, pageable)
                .map(tenant -> enrichWithBalance(tenant, landlordId));

        return PagedResponse.from(responses);
    }

    public TenantResponse getTenant(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        Tenant tenant = tenantRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        propertyAccessGuard.assertCanAccess(tenant.getProperty().getId());
        return enrichWithBalance(tenant, landlordId);
    }

    @Transactional
    public TenantResponse createTenant(TenantRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        propertyAccessGuard.assertCanAccess(request.propertyId());

        if (tenantRepository.existsByPhoneAndLandlordId(request.phone(), landlordId)) {
            throw new IllegalArgumentException(
                    "Tenant with phone number already exists: " + request.phone());
        }

        if (request.email() != null &&
                tenantRepository.existsByEmailAndLandlordId(request.email(), landlordId)) {
            throw new IllegalArgumentException(
                    "Tenant with email already exists: " + request.email());
        }

        Property property = propertyRepository
                .findByIdAndLandlordId(request.propertyId(), landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));

        Tenant tenant = Tenant.builder()
                .landlord(userRepository.getReferenceById(landlordId))
                .property(property)
                .name(request.name())
                .phone(request.phone())
                .email(request.email())
                .address(request.address())
                .build();

        // saveAndFlush (not save) so @CreationTimestamp's INSERT-time
        // population actually happens before this response is built —
        // otherwise createdAt comes back null until the next fetch, since
        // UUID-strategy inserts don't force an immediate flush.
        Tenant saved = tenantRepository.saveAndFlush(tenant);

        auditWriter.record(AuditModule.TENANT, AuditAction.CREATE,
                property.getId(), saved.getName(),
                "%s added tenant %s.".formatted(JwtUtils.getCurrentUserName(), saved.getName()));

        return TenantResponse.from(saved);
    }

    @Transactional
    public TenantResponse updateTenant(UUID id, TenantRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        Tenant tenant = tenantRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        propertyAccessGuard.assertCanAccess(tenant.getProperty().getId());

        List<String> changes = new ArrayList<>();
        AuditDiff.diff(changes, "name", tenant.getName(), request.name());
        AuditDiff.diff(changes, "phone", tenant.getPhone(), request.phone());
        AuditDiff.diff(changes, "email", tenant.getEmail(), request.email());
        AuditDiff.diff(changes, "address", tenant.getAddress(), request.address());

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

        Tenant saved = tenantRepository.save(tenant);

        if (!changes.isEmpty()) {
            auditWriter.record(AuditModule.TENANT, AuditAction.UPDATE,
                    saved.getProperty().getId(), saved.getName(),
                    "%s updated tenant %s: %s.".formatted(
                            JwtUtils.getCurrentUserName(), saved.getName(),
                            String.join("; ", changes)));
        }

        return TenantResponse.from(saved);
    }

    @Transactional
    public void deleteTenant(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        Tenant tenant = tenantRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        propertyAccessGuard.assertCanAccess(tenant.getProperty().getId());

        UUID propertyId = tenant.getProperty().getId();
        String name = tenant.getName();
        tenantRepository.delete(tenant);

        auditWriter.record(AuditModule.TENANT, AuditAction.DELETE, propertyId, name,
                "%s deleted tenant %s.".formatted(JwtUtils.getCurrentUserName(), name));
    }

    /**
     * Tenant transaction & arrears ledger — cycle-by-cycle breakdown plus the
     * raw payment history for an active agreement, so a balance like
     * "outstanding: 430,000" can be traced back to how it was derived.
     */
    public TenantLedgerResponse getTenantLedger(UUID tenantId) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        Tenant tenant = tenantRepository.findByIdAndLandlordId(tenantId, landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        propertyAccessGuard.assertCanAccess(tenant.getProperty().getId());

        RentalAgreement agreement = agreementRepository
                .findFirstByTenantIdAndLandlordIdAndStatus(
                        tenantId, landlordId, AgreementStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tenant has no active agreement"));

        BalanceSummary summary = computeBalanceSummary(agreement);

        LocalDate today = LocalDate.now();
        List<CycleStatusResponse> rawCycles = agreementService.computeCycleStatuses(agreement);

        List<Payment> allPayments = paymentRepository
                .findAllByAgreementIdOrderByPaymentDateAscCreatedAtAsc(agreement.getId());

        // Running balance is driven by real cash inflow only (CASH-sourced
        // payments, in the order it was received) — ROLLOVER rows are just a
        // re-labelling of that same cash against future cycles for display
        // purposes and must not be treated as additional money, or the
        // running balance double-counts overpayments the same way the old
        // sumAllByAgreement() did. Payments for a period before the
        // agreement's tracked cycle range (effectiveStartDate) are excluded
        // here too — they still show up in the transactions list below for
        // audit purposes, but must not silently offset later, actually-due
        // cycles the way they aren't counted as owed either.
        LocalDate cutoff = BillingCycleUtils.effectiveStartDate(agreement);
        List<Payment> cashPayments = allPayments.stream()
                .filter(p -> p.getSource() == PaymentSource.CASH)
                .filter(p -> !p.getPeriodStartDate().isBefore(cutoff))
                .sorted(java.util.Comparator.comparing(Payment::getPaymentDate))
                .toList();

        List<TenantLedgerResponse.CycleEntry> cycleEntries = new ArrayList<>();
        BigDecimal cumulativeExpected = summary.openingArrears().subtract(summary.openingCredit());
        BigDecimal cumulativeCash = BigDecimal.ZERO;
        int cashIdx = 0;

        for (CycleStatusResponse c : rawCycles) {
            boolean due = agreement.getBillingModel() == BillingModel.ARREARS
                    ? c.periodEndDate().isBefore(today)
                    : !c.periodStartDate().isAfter(today);

            if (due) {
                cumulativeExpected = cumulativeExpected.add(c.expectedAmount());
            }

            while (cashIdx < cashPayments.size()
                    && !cashPayments.get(cashIdx).getPaymentDate().isAfter(c.periodEndDate())) {
                cumulativeCash = cumulativeCash.add(cashPayments.get(cashIdx).getAmount());
                cashIdx++;
            }

            BigDecimal running = cumulativeExpected.subtract(cumulativeCash);

            cycleEntries.add(new TenantLedgerResponse.CycleEntry(
                    c.periodStartDate(), c.periodEndDate(), c.expectedAmount(),
                    c.paidAmount(), running, c.status(), due));
        }

        // Most recent payment first for display, newest-created breaking ties on
        // the same date — the exact order the Payments endpoint uses (paymentDate
        // desc, then createdAt desc), so a tenant's Transaction History and the
        // Payments table list identical rows in identical order. The balance walk
        // above already consumed allPayments/cashPayments in chronological order.
        // Only a bounded preview ships here; the rest is fetched on demand via
        // getTenantTransactions() ("load more") so a long tenancy doesn't inflate
        // this payload over time. ROLLOVER rows are kept — they're real payment
        // records and appear on the Payments table too.
        List<PaymentResponse> transactions = allPayments.stream()
                .sorted(java.util.Comparator.comparing(Payment::getPaymentDate)
                        .thenComparing(Payment::getCreatedAt)
                        .reversed())
                .limit(TRANSACTIONS_PREVIEW_SIZE)
                .map(PaymentResponse::from).toList();

        return new TenantLedgerResponse(
                tenant.getId(), tenant.getName(), agreement.getId(),
                agreement.getUnit().getRoomNumber(), agreement.getRentAmount(),
                agreement.getBillingModel().name(),
                summary.openingArrears(), summary.openingCredit(),
                summary.totalEverOwed(), summary.totalEverPaid(), summary.outstanding(),
                cycleEntries, transactions, allPayments.size()
        );
    }

    private static final int TRANSACTIONS_PREVIEW_SIZE = 15;

    /**
     * Paginated payment history for a tenant's active agreement — backs
     * "load more" in the ledger view once past the initial preview page.
     */
    public PagedResponse<PaymentResponse> getTenantTransactions(UUID tenantId, Pageable pageable) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        Tenant tenant = tenantRepository.findByIdAndLandlordId(tenantId, landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        propertyAccessGuard.assertCanAccess(tenant.getProperty().getId());

        RentalAgreement agreement = agreementRepository
                .findFirstByTenantIdAndLandlordIdAndStatus(
                        tenantId, landlordId, AgreementStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tenant has no active agreement"));

        Page<Payment> page = paymentRepository.findAllByLandlordIdAndAgreementId(
                landlordId, agreement.getId(), pageable);

        return PagedResponse.from(page.map(PaymentResponse::from));
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private record BalanceSummary(
            BigDecimal totalEverOwed,
            BigDecimal totalEverPaid,
            BigDecimal outstanding,
            BigDecimal openingCredit,
            BigDecimal openingArrears
    ) {
    }

    private BalanceSummary computeBalanceSummary(RentalAgreement agreement) {
        // Sum of rent for every cycle actually due so far — derived from the
        // exact same per-cycle walk (computeCycleStatuses) the ledger and the
        // Payments page cycle picker use, rather than a separate closed-form
        // "cyclesElapsed × rent" formula. Two independent implementations of
        // "how many cycles are owed" can drift apart on edge cases (e.g. they
        // used to disagree for agreements with no explicit startDate); this
        // way there's exactly one.
        LocalDate today = LocalDate.now();
        BigDecimal dueExpected = BigDecimal.ZERO;
        for (CycleStatusResponse cycle : agreementService.computeCycleStatuses(agreement)) {
            boolean due = agreement.getBillingModel() == BillingModel.ARREARS
                    ? cycle.periodEndDate().isBefore(today)
                    : !cycle.periodStartDate().isAfter(today);
            if (due) {
                dueExpected = dueExpected.add(cycle.expectedAmount());
            }
        }

        // Apply opening balance
        BigDecimal openingCredit = agreement.getOpeningBalance().max(BigDecimal.ZERO);
        BigDecimal openingArrears = agreement.getOpeningBalance().min(BigDecimal.ZERO).abs();
        BigDecimal totalEverOwed = dueExpected.subtract(openingCredit).add(openingArrears);

        // Same cutoff the cycle walk above starts counting cycles from — a
        // payment recorded for a period before this (a prior arrangement,
        // stray data entry, an import) was never counted as owed above, so
        // it must not be allowed to silently cancel out arrears on a later,
        // actually-due cycle. Also subtract overpayment already re-recorded
        // via rollover rows, so a lump-sum payment isn't credited twice
        // (once on the original CASH row, again on the ROLLOVER rows it spawned).
        LocalDate cutoff = BillingCycleUtils.effectiveStartDate(agreement);
        BigDecimal totalEverPaid = paymentRepository
                .sumByAgreementFromDate(agreement.getId(), cutoff)
                .subtract(paymentRepository.sumOverpaymentByAgreementAndSourceFromDate(
                        agreement.getId(), PaymentSource.CASH, cutoff));

        // Outstanding
        BigDecimal outstanding = totalEverOwed.subtract(totalEverPaid)
                .max(BigDecimal.ZERO);

        return new BalanceSummary(
                totalEverOwed, totalEverPaid, outstanding, openingCredit, openingArrears);
    }

    private TenantResponse enrichWithBalance(Tenant tenant, UUID landlordId) {

        Optional<RentalAgreement> activeAgreement = agreementRepository
                .findFirstByTenantIdAndLandlordIdAndStatus(
                        tenant.getId(), landlordId, AgreementStatus.ACTIVE);

        if (activeAgreement.isEmpty()) {
            return TenantResponse.from(tenant);
        }

        RentalAgreement agreement = activeAgreement.get();

        BalanceSummary summary = computeBalanceSummary(agreement);
        BigDecimal outstanding = summary.outstanding();
        BigDecimal openingArrears = summary.openingArrears();
        BigDecimal totalEverOwed = summary.totalEverOwed();
        BigDecimal totalEverPaid = summary.totalEverPaid();

        // Current cycle dates — may be null for ARREARS tenant
        // whose first cycle has not yet completed
        LocalDate cycleStart = BillingCycleUtils.currentCycleStart(agreement);

        // ── Guard: no cycle due yet ──────────────────────────
        if (cycleStart == null) {
            // ARREARS tenant — first cycle not yet complete, nothing is due
            String periodStatus = outstanding.compareTo(BigDecimal.ZERO) == 0
                    ? "PAID" : "PARTIAL";

            return TenantResponse.from(tenant).withBalance(
                    agreement.getUnit().getRoomNumber(),
                    agreement.getRentAmount(),
                    outstanding,
                    openingArrears,
                    totalEverOwed,
                    totalEverPaid,
                    periodStatus,
                    null,   // no current cycle start
                    null,   // no current cycle end
                    false   // current cycle not paid (nothing due yet)
            );
        }

        // Current cycle end
        LocalDate cycleEnd = BillingCycleUtils.cycleEnd(cycleStart, agreement.getBillingDay());

        // Sum payments for current cycle specifically
        BigDecimal currentCyclePaidAmount = paymentRepository.sumByAgreementAndCycle(
                agreement.getId(), cycleStart, cycleEnd);

        boolean currentCyclePaid = currentCyclePaidAmount
                .compareTo(agreement.getRentAmount()) >= 0;

        // Only roll the displayed "current" cycle forward once the tenant is
        // genuinely fully settled (outstanding == 0) — not just because the
        // next cycle happens to have *any* payment against it. Rolling
        // forward on a mere partial/advance payment misleadingly showed a
        // barely-started future cycle as "current" for a tenant who wasn't
        // actually paid up.
        if (currentCyclePaid && outstanding.compareTo(BigDecimal.ZERO) == 0) {
            LocalDate nextStart = BillingCycleUtils.nextCycleStart(cycleStart, agreement.getBillingDay());
            LocalDate nextEnd = BillingCycleUtils.cycleEnd(nextStart, agreement.getBillingDay());

            BigDecimal nextCyclePaidAmount = paymentRepository.sumByAgreementAndCycle(
                    agreement.getId(), nextStart, nextEnd);

            // Use next cycle for the "current" display if it has been paid
            if (nextCyclePaidAmount.compareTo(BigDecimal.ZERO) > 0) {
                cycleStart = nextStart;
                cycleEnd = nextEnd;
                currentCyclePaid = nextCyclePaidAmount
                        .compareTo(agreement.getRentAmount()) >= 0;
            }
        }

        // Period status
        String periodStatus;
        if (outstanding.compareTo(BigDecimal.ZERO) == 0) {
            periodStatus = "PAID";
        } else if (totalEverPaid.compareTo(BigDecimal.ZERO) > 0) {
            periodStatus = "PARTIAL";
        } else {
            periodStatus = "UNPAID";
        }

        return TenantResponse.from(tenant).withBalance(
                agreement.getUnit().getRoomNumber(),
                agreement.getRentAmount(),
                outstanding,
                openingArrears,
                totalEverOwed,
                totalEverPaid,
                periodStatus,
                cycleStart,
                cycleEnd,
                currentCyclePaid
        );
    }
}