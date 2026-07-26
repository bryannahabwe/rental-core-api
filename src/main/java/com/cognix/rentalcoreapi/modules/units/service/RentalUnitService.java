package com.cognix.rentalcoreapi.modules.units.service;

import com.cognix.rentalcoreapi.modules.audit.AuditDiff;
import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.service.AuditWriter;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.properties.model.Property;
import com.cognix.rentalcoreapi.modules.properties.repository.PropertyRepository;
import com.cognix.rentalcoreapi.modules.units.dto.RentalUnitRequest;
import com.cognix.rentalcoreapi.modules.units.dto.RentalUnitResponse;
import com.cognix.rentalcoreapi.modules.units.model.RentalUnit;
import com.cognix.rentalcoreapi.modules.units.repository.RentalUnitRepository;
import com.cognix.rentalcoreapi.shared.response.PagedResponse;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import com.cognix.rentalcoreapi.shared.security.PropertyAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RentalUnitService {

    private final RentalUnitRepository rentalUnitRepository;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyAccessGuard propertyAccessGuard;
    private final AuditWriter auditWriter;

    public PagedResponse<RentalUnitResponse> getAllUnits(
            Pageable pageable, String search, Boolean isAvailable) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        UUID propertyId = propertyAccessGuard.requireAccessibleProperty();
        Page<RentalUnitResponse> page = rentalUnitRepository
                .findAllByLandlordIdWithSearch(landlordId, propertyId, search, isAvailable, pageable)
                .map(RentalUnitResponse::from);
        return PagedResponse.from(page);
    }

    public RentalUnitResponse getUnit(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        RentalUnit unit = rentalUnitRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Rental unit not found"));
        propertyAccessGuard.assertCanAccess(unit.getProperty().getId());
        return RentalUnitResponse.from(unit);
    }

    @Transactional
    public RentalUnitResponse createUnit(RentalUnitRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        propertyAccessGuard.assertCanAccess(request.propertyId());

        if (rentalUnitRepository.existsByRoomNumberAndLandlordId(
                request.roomNumber(), landlordId)) {
            throw new IllegalArgumentException(
                    "Room number already exists: " + request.roomNumber());
        }

        Property property = resolveProperty(request.propertyId(), landlordId);

        var landlord = userRepository.getReferenceById(landlordId);

        RentalUnit unit = RentalUnit.builder()
                .landlord(landlord)
                .property(property)
                .roomNumber(request.roomNumber())
                .description(request.description())
                .rentAmount(request.rentAmount())
                .isAvailable(request.isAvailable())
                .build();

        // saveAndFlush (not save) so @CreationTimestamp's INSERT-time
        // population actually happens before this response is built.
        RentalUnit saved = rentalUnitRepository.saveAndFlush(unit);

        auditWriter.record(AuditModule.UNIT, AuditAction.CREATE,
                property.getId(), saved.getRoomNumber(),
                "%s added unit %s.".formatted(JwtUtils.getCurrentUserName(), saved.getRoomNumber()));

        return RentalUnitResponse.from(saved);
    }

    @Transactional
    public RentalUnitResponse updateUnit(UUID id, RentalUnitRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        RentalUnit unit = rentalUnitRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Rental unit not found"));
        propertyAccessGuard.assertCanAccess(unit.getProperty().getId());

        List<String> changes = new ArrayList<>();
        AuditDiff.diff(changes, "room", unit.getRoomNumber(), request.roomNumber());
        AuditDiff.diff(changes, "rent", unit.getRentAmount(), request.rentAmount());
        AuditDiff.diff(changes, "description", unit.getDescription(), request.description());
        AuditDiff.diff(changes, "available", unit.isAvailable(), request.isAvailable());

        unit.setRoomNumber(request.roomNumber());
        unit.setDescription(request.description());
        unit.setRentAmount(request.rentAmount());
        unit.setAvailable(request.isAvailable());

        RentalUnit saved = rentalUnitRepository.save(unit);

        if (!changes.isEmpty()) {
            auditWriter.record(AuditModule.UNIT, AuditAction.UPDATE,
                    saved.getProperty().getId(), saved.getRoomNumber(),
                    "%s updated unit %s: %s.".formatted(
                            JwtUtils.getCurrentUserName(), saved.getRoomNumber(),
                            String.join("; ", changes)));
        }

        return RentalUnitResponse.from(saved);
    }

    @Transactional
    public void deleteUnit(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        RentalUnit unit = rentalUnitRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Rental unit not found"));
        propertyAccessGuard.assertCanAccess(unit.getProperty().getId());

        UUID propertyId = unit.getProperty().getId();
        String room = unit.getRoomNumber();
        rentalUnitRepository.delete(unit);

        auditWriter.record(AuditModule.UNIT, AuditAction.DELETE, propertyId, room,
                "%s deleted unit %s.".formatted(JwtUtils.getCurrentUserName(), room));
    }

    private Property resolveProperty(UUID propertyId, UUID landlordId) {
        return propertyRepository.findByIdAndLandlordId(propertyId, landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));
    }
}