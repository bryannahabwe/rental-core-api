package com.cognix.rentalcoreapi.modules.paymentmethods.repository;

import com.cognix.rentalcoreapi.modules.paymentmethods.model.PaymentMethodOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentMethodOptionRepository extends JpaRepository<PaymentMethodOption, UUID> {

    List<PaymentMethodOption> findAllByLandlordIdOrderByNameAsc(UUID landlordId);

    Optional<PaymentMethodOption> findByIdAndLandlordId(UUID id, UUID landlordId);

    Optional<PaymentMethodOption> findByLandlordIdAndNameIgnoreCase(UUID landlordId, String name);

    boolean existsByLandlordIdAndNameIgnoreCase(UUID landlordId, String name);
}
