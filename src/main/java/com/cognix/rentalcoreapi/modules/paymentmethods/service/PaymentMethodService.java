package com.cognix.rentalcoreapi.modules.paymentmethods.service;

import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.service.AuditWriter;
import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.paymentmethods.dto.PaymentMethodRequest;
import com.cognix.rentalcoreapi.modules.paymentmethods.dto.PaymentMethodResponse;
import com.cognix.rentalcoreapi.modules.paymentmethods.model.PaymentMethodOption;
import com.cognix.rentalcoreapi.modules.paymentmethods.repository.PaymentMethodOptionRepository;
import com.cognix.rentalcoreapi.shared.exception.ConflictException;
import com.cognix.rentalcoreapi.shared.exception.NotFoundException;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Account-managed payment-method options. Expenses store the chosen method as a
 * name string (not an FK); this list just supplies the pick-list. Audited under
 * {@link AuditModule#SETTINGS} since it's account configuration.
 */
@Service
@RequiredArgsConstructor
public class PaymentMethodService {

    static final List<String> DEFAULT_METHODS = List.of(
            "Cash", "Mobile Money", "Bank Transfer", "Cheque");
    private static final String FALLBACK = "Other";

    private final PaymentMethodOptionRepository methodRepository;
    private final UserRepository userRepository;
    private final AuditWriter auditWriter;

    public List<PaymentMethodResponse> getAll() {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        return methodRepository.findAllByLandlordIdOrderByNameAsc(landlordId)
                .stream().map(PaymentMethodResponse::from).toList();
    }

    @Transactional
    public PaymentMethodResponse create(PaymentMethodRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        String name = request.name().trim();

        if (methodRepository.existsByLandlordIdAndNameIgnoreCase(landlordId, name)) {
            throw new ConflictException("A payment method with this name already exists: " + name);
        }

        PaymentMethodOption saved = methodRepository.save(PaymentMethodOption.builder()
                .landlord(userRepository.getReferenceById(landlordId))
                .name(name)
                .active(request.active() == null || request.active())
                .build());

        auditWriter.record(AuditModule.SETTINGS, AuditAction.CREATE, null, saved.getId().toString(),
                "%s added the payment method \"%s\".".formatted(JwtUtils.getCurrentUserName(), name));
        return PaymentMethodResponse.from(saved);
    }

    @Transactional
    public PaymentMethodResponse update(UUID id, PaymentMethodRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        PaymentMethodOption method = methodRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new NotFoundException("Payment method not found"));

        String name = request.name().trim();
        if (!method.getName().equalsIgnoreCase(name)
                && methodRepository.existsByLandlordIdAndNameIgnoreCase(landlordId, name)) {
            throw new ConflictException("A payment method with this name already exists: " + name);
        }

        method.setName(name);
        if (request.active() != null) {
            method.setActive(request.active());
        }
        PaymentMethodOption saved = methodRepository.save(method);

        auditWriter.record(AuditModule.SETTINGS, AuditAction.UPDATE, null, saved.getId().toString(),
                "%s updated the payment method \"%s\".".formatted(JwtUtils.getCurrentUserName(), name));
        return PaymentMethodResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        PaymentMethodOption method = methodRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new NotFoundException("Payment method not found"));

        // Soft-retire: expenses keep the method as a name string, so hiding the
        // option from pickers is enough and leaves past rows intact.
        method.setActive(false);
        methodRepository.save(method);

        auditWriter.record(AuditModule.SETTINGS, AuditAction.DELETE, null, id.toString(),
                "%s removed the payment method \"%s\".".formatted(
                        JwtUtils.getCurrentUserName(), method.getName()));
    }

    /**
     * Normalizes a free-text method to its canonical stored name, creating the
     * option if it doesn't exist yet. Blank → "Other". Called when recording an
     * expense so the method always matches a filterable option.
     */
    @Transactional
    public String resolveOrCreate(UUID landlordId, String rawName) {
        String name = rawName == null || rawName.isBlank() ? FALLBACK : rawName.trim();
        return methodRepository.findByLandlordIdAndNameIgnoreCase(landlordId, name)
                .map(PaymentMethodOption::getName)
                .orElseGet(() -> methodRepository.save(PaymentMethodOption.builder()
                        .landlord(userRepository.getReferenceById(landlordId))
                        .name(name).active(true).build()).getName());
    }

    /** Seeds the default methods for a new account (idempotent per name). */
    @Transactional
    public void seedDefaults(User owner) {
        for (String name : DEFAULT_METHODS) {
            if (!methodRepository.existsByLandlordIdAndNameIgnoreCase(owner.getId(), name)) {
                methodRepository.save(PaymentMethodOption.builder()
                        .landlord(owner).name(name).active(true).build());
            }
        }
    }
}
