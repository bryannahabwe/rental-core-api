package com.cognix.rentalcoreapi.modules.payments.service;

import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import com.cognix.rentalcoreapi.modules.agreements.repository.RentalAgreementRepository;
import com.cognix.rentalcoreapi.modules.payments.model.Payment;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentMethod;
import com.cognix.rentalcoreapi.modules.payments.model.PaymentSource;
import com.cognix.rentalcoreapi.modules.payments.repository.PaymentRepository;
import com.cognix.rentalcoreapi.shared.util.BillingCycleUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rebuilds every figure an agreement derives from its payments: each CASH row's
 * {@code overpayment}, the whole ROLLOVER chain, and the effective opening
 * balance. Idempotent — running it twice changes nothing the second time.
 *
 * <p>The scoped equivalent of V30's replay, and the reason a payment can be
 * corrected at all. Deleting or editing one CASH row invalidates every rollover
 * row downstream of it and can re-size rollovers from *later* payments too, so
 * there is no such thing as undoing one payment's chain in isolation: the
 * agreement is replayed from the rows that remain.
 */
@Service
@RequiredArgsConstructor
public class PaymentAllocationService {

    private final PaymentRepository paymentRepository;
    private final RentalAgreementRepository agreementRepository;
    private final RolloverAllocator allocator;

    /** What the replay changed, so the caller can say so in the audit trail. */
    public record Result(int rolloversBefore, int rolloversAfter, BigDecimal arrearsPaid) {
    }

    /**
     * A rollover row reduced to what makes it interchangeable with another. Two
     * rows carrying the same credit for the same cycle on the same date are the
     * same row as far as every reader is concerned.
     */
    private record RolloverKey(LocalDate periodStart, LocalDate periodEnd,
                               BigDecimal amount, LocalDate paymentDate) {
    }

    @Transactional
    public Result reallocate(RentalAgreement agreement) {
        List<Payment> rows = paymentRepository
                .findAllByAgreementIdOrderByCreatedAtAscIdAsc(agreement.getId());

        List<Payment> cash = rows.stream()
                .filter(p -> p.getSource() == PaymentSource.CASH).toList();
        List<Payment> existingRollovers = rows.stream()
                .filter(p -> p.getSource() == PaymentSource.ROLLOVER).toList();

        RolloverAllocator.Allocation plan = allocator.allocate(
                agreement.getRentAmount(),
                agreement.getBillingDay(),
                BillingCycleUtils.effectiveStartDate(agreement),
                cash.stream()
                        .map(p -> new RolloverAllocator.CashRow(
                                p.getId(), p.getAmount(),
                                p.getPeriodStartDate(), p.getPeriodEndDate()))
                        .toList());

        // CASH rows are the record of money actually received and are never
        // modified here beyond `overpayment` — itself a derived figure recording
        // how much of that payment spilled out of its own cycle.
        for (Payment p : cash) {
            BigDecimal want = plan.overpaymentByCashId().get(p.getId());
            if (want != null && p.getOverpayment().compareTo(want) != 0) {
                p.setOverpayment(want);
            }
        }

        applyChain(agreement, cash, existingRollovers, plan.rollovers());

        // Derived from the rows rather than adjusted in place, so correcting a
        // payment needs no compensating arithmetic. See V32's header.
        agreement.setOpeningBalance(nz(agreement.getOpeningBalanceEntered())
                .add(plan.arrearsPaid())
                .add(nz(agreement.getDepositApplied())));
        agreementRepository.save(agreement);

        // Callers read per-cycle totals straight after this; without the flush
        // they would read the pre-replay state.
        paymentRepository.flush();

        return new Result(existingRollovers.size(), plan.rollovers().size(), plan.arrearsPaid());
    }

    /**
     * Reconciles the stored chain with the one the allocation calls for, as a
     * bag diff rather than a blind delete-and-rewrite.
     *
     * <p>Rewriting wholesale would change every rollover row's id and
     * {@code createdAt} on every payment write — and {@code Payment} has no
     * {@code updatedAt}, so {@code createdAt} cannot be preserved on reinsert —
     * reshuffling the tenant ledger, which sorts on it. Recording a payment only
     * ever appends to the chain, so the diff leaves it untouched; an edit
     * rewrites only the rows that actually moved.
     */
    private void applyChain(RentalAgreement agreement, List<Payment> cash,
                            List<Payment> existing, List<RolloverAllocator.RolloverRow> wanted) {

        Map<UUID, Payment> funders = new HashMap<>();
        for (Payment p : cash) {
            funders.put(p.getId(), p);
        }

        Map<RolloverKey, Deque<Payment>> reusable = new HashMap<>();
        for (Payment p : existing) {
            reusable.computeIfAbsent(keyOf(p), k -> new ArrayDeque<>()).add(p);
        }

        List<Payment> toInsert = new ArrayList<>();
        for (RolloverAllocator.RolloverRow row : wanted) {
            Payment funder = funders.get(row.fundedBy());
            Deque<Payment> matches = reusable.get(keyOf(row, funder));
            if (matches == null || matches.isEmpty()) {
                toInsert.add(rolloverEntity(agreement, funder, row));
            } else {
                Payment kept = matches.poll();
                // Backfills the link on rows written before V32.
                kept.setFundedByPaymentId(funder.getId());
            }
        }

        List<Payment> toDelete = reusable.values().stream().flatMap(Deque::stream).toList();
        if (!toDelete.isEmpty()) {
            paymentRepository.deleteAll(toDelete);
            // The DELETE must reach the database before the INSERTs below, or a
            // re-created row can collide with the one it replaces.
            paymentRepository.flush();
        }
        if (!toInsert.isEmpty()) {
            paymentRepository.saveAll(toInsert);
        }
    }

    private static RolloverKey keyOf(Payment p) {
        return new RolloverKey(p.getPeriodStartDate(), p.getPeriodEndDate(),
                p.getAmount().stripTrailingZeros(), p.getPaymentDate());
    }

    private static RolloverKey keyOf(RolloverAllocator.RolloverRow row, Payment funder) {
        return new RolloverKey(row.periodStart(), row.periodEnd(),
                row.amount().stripTrailingZeros(), funder.getPaymentDate());
    }

    private static Payment rolloverEntity(RentalAgreement agreement, Payment funder,
                                          RolloverAllocator.RolloverRow row) {
        return Payment.builder()
                .landlord(funder.getLandlord())
                .property(agreement.getProperty())
                .tenant(agreement.getTenant())
                .unit(agreement.getUnit())
                .agreement(agreement)
                .paymentDate(funder.getPaymentDate())
                .amount(row.amount())
                .method(PaymentMethod.CASH)
                .periodStartDate(row.periodStart())
                .periodEndDate(row.periodEnd())
                .expectedAmount(agreement.getRentAmount())
                // This row's `amount` is already the credit for THIS cycle
                // (capped at what the cycle still needed). Any leftover is
                // carried by the NEXT rollover row — NOT by this row's
                // overpayment. Storing it here would make `amount - overpayment`
                // (the per-cycle retained figure) net to zero for every middle
                // cycle.
                .overpayment(BigDecimal.ZERO)
                .source(PaymentSource.ROLLOVER)
                .fundedByPaymentId(funder.getId())
                .reference("Rollover from " + row.periodStart().minusDays(1))
                .notes(null)
                .build();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
