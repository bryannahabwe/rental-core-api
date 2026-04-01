package com.cognix.rentalcoreapi.modules.payments.repository;

import com.cognix.rentalcoreapi.modules.payments.model.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Page<Payment> findAllByLandlordId(UUID landlordId, Pageable pageable);

    Page<Payment> findAllByLandlordIdAndTenantId(
            UUID landlordId, UUID tenantId, Pageable pageable);

    Page<Payment> findAllByLandlordIdAndAgreementId(
            UUID landlordId, UUID agreementId, Pageable pageable);

    Optional<Payment> findByIdAndLandlordId(UUID id, UUID landlordId);

    List<Payment> findAllByLandlordIdAndPaymentDateBetween(
            UUID landlordId, LocalDate from, LocalDate to);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "WHERE p.landlord.id = :landlordId " +
            "AND p.paymentDate BETWEEN :from AND :to")
    BigDecimal sumAmountByLandlordIdAndDateRange(
            UUID landlordId, LocalDate from, LocalDate to);
}