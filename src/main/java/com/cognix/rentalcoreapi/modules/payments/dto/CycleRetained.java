package com.cognix.rentalcoreapi.modules.payments.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * How much one billing cycle actually retains, summed across every payment
 * row filed against it: {@code SUM(amount - overpayment)}.
 *
 * <p>Projection target for
 * {@link com.cognix.rentalcoreapi.modules.payments.repository.PaymentRepository#findRetainedByCycle},
 * which resolves a whole page of payments in one grouped query.
 */
public record CycleRetained(
        UUID agreementId,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        BigDecimal retained
) {
}
