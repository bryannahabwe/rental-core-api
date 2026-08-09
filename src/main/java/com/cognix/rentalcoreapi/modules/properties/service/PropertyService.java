package com.cognix.rentalcoreapi.modules.properties.service;

import com.cognix.rentalcoreapi.modules.audit.AuditDiff;
import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.service.AuditWriter;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.properties.dto.PropertyRequest;
import com.cognix.rentalcoreapi.modules.properties.dto.PropertyResponse;
import com.cognix.rentalcoreapi.modules.properties.model.Property;
import com.cognix.rentalcoreapi.modules.properties.repository.PropertyRepository;
import com.cognix.rentalcoreapi.modules.tenants.repository.TenantRepository;
import com.cognix.rentalcoreapi.modules.units.repository.RentalUnitRepository;
import com.cognix.rentalcoreapi.modules.users.repository.UserPropertyAssignmentRepository;
import com.cognix.rentalcoreapi.shared.exception.ConflictException;
import com.cognix.rentalcoreapi.shared.exception.NotFoundException;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import com.cognix.rentalcoreapi.shared.security.PropertyAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final RentalUnitRepository unitRepository;
    private final TenantRepository tenantRepository;
    private final UserPropertyAssignmentRepository assignmentRepository;
    private final PropertyAccessGuard propertyAccessGuard;
    private final AuditWriter auditWriter;

    public List<PropertyResponse> getAllProperties() {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        List<Property> properties =
                propertyRepository.findAllByLandlordIdOrderByCreatedAtAsc(landlordId);

        // Scoped staff see only the properties assigned to them.
        if (JwtUtils.getCurrentRole().isPropertyScoped()) {
            Set<UUID> assigned = Set.copyOf(
                    assignmentRepository.findPropertyIdsByUserId(JwtUtils.getCurrentUserId()));
            properties = properties.stream()
                    .filter(p -> assigned.contains(p.getId()))
                    .toList();
        }

        return properties.stream().map(this::toResponse).toList();
    }

    public PropertyResponse getProperty(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        Property property = propertyRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new NotFoundException("Property not found"));
        propertyAccessGuard.assertCanAccess(property.getId());
        return toResponse(property);
    }

    @Transactional
    public PropertyResponse createProperty(PropertyRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        if (propertyRepository.existsByNameAndLandlordId(request.name(), landlordId)) {
            throw new ConflictException(
                    "A property with this name already exists: " + request.name());
        }

        Property property = Property.builder()
                .landlord(userRepository.getReferenceById(landlordId))
                .name(request.name())
                .address(request.address())
                .description(request.description())
                .build();

        // saveAndFlush (not save) so @CreationTimestamp's INSERT-time
        // population actually happens before this response is built.
        Property saved = propertyRepository.saveAndFlush(property);

        auditWriter.record(AuditModule.PROPERTY, AuditAction.CREATE,
                saved.getId(), saved.getName(),
                "%s created property %s.".formatted(JwtUtils.getCurrentUserName(), saved.getName()));

        return toResponse(saved);
    }

    @Transactional
    public PropertyResponse updateProperty(UUID id, PropertyRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        Property property = propertyRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new NotFoundException("Property not found"));

        if (!property.getName().equals(request.name())
                && propertyRepository.existsByNameAndLandlordId(request.name(), landlordId)) {
            throw new ConflictException(
                    "A property with this name already exists: " + request.name());
        }

        List<String> changes = new ArrayList<>();
        AuditDiff.diff(changes, "name", property.getName(), request.name());
        AuditDiff.diff(changes, "address", property.getAddress(), request.address());
        AuditDiff.diff(changes, "description", property.getDescription(), request.description());

        property.setName(request.name());
        property.setAddress(request.address());
        property.setDescription(request.description());

        Property saved = propertyRepository.save(property);

        if (!changes.isEmpty()) {
            auditWriter.record(AuditModule.PROPERTY, AuditAction.UPDATE,
                    saved.getId(), saved.getName(),
                    "%s updated property %s: %s.".formatted(
                            JwtUtils.getCurrentUserName(), saved.getName(),
                            String.join("; ", changes)));
        }

        return toResponse(saved);
    }

    @Transactional
    public void deleteProperty(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        Property property = propertyRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new NotFoundException("Property not found"));

        long units = unitRepository.countByPropertyId(id);
        long tenants = tenantRepository.countByPropertyId(id);

        if (units > 0 || tenants > 0) {
            throw new ConflictException(
                    "Cannot delete a property that still has units or tenants. "
                            + "Move or remove them first.");
        }

        String name = property.getName();
        propertyRepository.delete(property);

        auditWriter.record(AuditModule.PROPERTY, AuditAction.DELETE, id, name,
                "%s deleted property %s.".formatted(JwtUtils.getCurrentUserName(), name));
    }

    private PropertyResponse toResponse(Property property) {
        return PropertyResponse.from(
                property,
                unitRepository.countByPropertyId(property.getId()),
                tenantRepository.countByPropertyId(property.getId()));
    }
}
