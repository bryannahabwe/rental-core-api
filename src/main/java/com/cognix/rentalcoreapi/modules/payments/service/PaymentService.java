package com.cognix.rentalcoreapi.modules.payments.service;

import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.repository.RentalAgreementRepository;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.payments.dto.PaymentRequest;
import com.cognix.rentalcoreapi.modules.payments.dto.PaymentResponse;
import com.cognix.rentalcoreapi.modules.payments.model.Payment;
import com.cognix.rentalcoreapi.modules.payments.repository.PaymentRepository;
import com.cognix.rentalcoreapi.shared.response.PagedResponse;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RentalAgreementRepository agreementRepository;
    private final UserRepository userRepository;

    public PagedResponse<PaymentResponse> getAllPayments(
            Pageable pageable, UUID tenantId, UUID agreementId,
            String search, LocalDate from, LocalDate to) {

        UUID landlordId = JwtUtils.getCurrentLandlordId();

        Page<Payment> page;

        if (from != null && to != null) {
            page = paymentRepository.findAllWithFiltersAndDates(
                    landlordId, tenantId, agreementId, search, from, to, pageable);
        } else {
            page = paymentRepository.findAllWithFilters(
                    landlordId, tenantId, agreementId, search, pageable);
        }

        return PagedResponse.from(page.map(PaymentResponse::from));
    }

    public PaymentResponse getPayment(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        return paymentRepository.findByIdAndLandlordId(id, landlordId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
    }

    public PaymentResponse recordPayment(PaymentRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        // ensure agreement belongs to this landlord
        var agreement = agreementRepository.findByIdAndLandlordId(
                        request.agreementId(), landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Agreement not found"));

        // ensure agreement is still active
        if (agreement.getStatus() == AgreementStatus.TERMINATED) {
            throw new IllegalArgumentException(
                    "Cannot record payment for a terminated agreement");
        }

        Payment payment = Payment.builder()
                .landlord(userRepository.getReferenceById(landlordId))
                .tenant(agreement.getTenant())
                .unit(agreement.getUnit())
                .agreement(agreement)
                .paymentDate(request.paymentDate())
                .amount(request.amount())
                .method(request.method())
                .reference(request.reference())
                .notes(request.notes())
                .build();

        return PaymentResponse.from(paymentRepository.save(payment));
    }
}