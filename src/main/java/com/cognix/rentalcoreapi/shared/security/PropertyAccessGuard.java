package com.cognix.rentalcoreapi.shared.security;

import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import com.cognix.rentalcoreapi.modules.users.repository.UserPropertyAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Enforces the PROPERTY_MANAGER → assigned-properties restriction, which role
 * checks alone can't express. Admins/owners are unrestricted; a manager may only
 * touch properties assigned to them.
 */
@Component
@RequiredArgsConstructor
public class PropertyAccessGuard {

    private final UserPropertyAssignmentRepository assignmentRepository;

    /**
     * Throws if the current user is a PROPERTY_MANAGER not assigned to
     * {@code propertyId}. No-op for admins/owners.
     */
    public void assertCanAccess(UUID propertyId) {
        if (JwtUtils.getCurrentRole() != UserRole.PROPERTY_MANAGER) {
            return;
        }
        if (propertyId == null
                || !assignmentRepository.existsByUserIdAndPropertyId(
                        JwtUtils.getCurrentUserId(), propertyId)) {
            throw new AccessDeniedException("You are not assigned to this property");
        }
    }

    /**
     * Resolves the effective property filter for a scoped read.
     * <ul>
     *   <li>Admins/owners: the selected property, or {@code null} for the
     *       landlord-wide "All properties" aggregate.</li>
     *   <li>Managers: must have a specific assigned property selected — returns
     *       it, or throws (they get no aggregate view).</li>
     * </ul>
     */
    public UUID requireAccessibleProperty() {
        UUID selected = JwtUtils.getCurrentPropertyId().orElse(null);
        if (JwtUtils.getCurrentRole() == UserRole.PROPERTY_MANAGER) {
            assertCanAccess(selected); // throws if null or unassigned
        }
        return selected;
    }
}
