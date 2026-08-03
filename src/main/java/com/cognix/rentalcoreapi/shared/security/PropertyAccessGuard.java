package com.cognix.rentalcoreapi.shared.security;

import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import com.cognix.rentalcoreapi.modules.users.repository.UserPropertyAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Enforces the property-scoped → assigned-properties restriction, which role
 * checks alone can't express.
 *
 * <p>The two layers divide cleanly: {@code @PreAuthorize} answers <em>may this
 * role do this kind of thing at all</em>, using the role the user holds for the
 * active property; this guard answers <em>is this particular property one of
 * theirs</em>. Account-wide roles pass straight through; scoped staff may only
 * touch properties assigned to them.
 */
@Component
@RequiredArgsConstructor
public class PropertyAccessGuard {

    private final UserPropertyAssignmentRepository assignmentRepository;

    /**
     * Throws if the current user holds a property-scoped role and is not
     * assigned to {@code propertyId}. No-op for account-wide roles.
     */
    public void assertCanAccess(UUID propertyId) {
        if (!JwtUtils.getCurrentRole().isPropertyScoped()) {
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
     *   <li>Account-wide roles: the selected property, or {@code null} for the
     *       landlord-wide "All properties" aggregate.</li>
     *   <li>Scoped staff with a property selected: that property (or throws if
     *       it isn't assigned to them).</li>
     *   <li>Scoped staff with nothing selected: their default assigned property,
     *       so they get a working view instead of the empty "All properties"
     *       aggregate they have no access to. Throws only if they have no
     *       assignments at all.</li>
     * </ul>
     */
    public UUID requireAccessibleProperty() {
        UUID selected = JwtUtils.getCurrentPropertyId().orElse(null);
        if (!JwtUtils.getCurrentRole().isPropertyScoped()) {
            return selected;
        }
        if (selected != null) {
            assertCanAccess(selected); // throws if unassigned
            return selected;
        }
        UUID fallback = defaultPropertyFor(JwtUtils.getCurrentUserId());
        if (fallback == null) {
            throw new AccessDeniedException("You are not assigned to any property");
        }
        return fallback;
    }

    /**
     * The role this user effectively holds for the active property, which is
     * what every {@code @PreAuthorize} check is evaluated against.
     *
     * <p>Account-wide roles resolve to themselves. For scoped staff the role
     * comes from their assignment row for the selected property; with nothing
     * selected it comes from the same default property
     * {@link #requireAccessibleProperty()} falls back to, so the role in the
     * security context and the data this guard hands back can never disagree.
     *
     * <p>Takes its arguments explicitly rather than reading {@link JwtUtils},
     * because the caller ({@code JwtAuthFilter}) is what populates the security
     * context in the first place.
     */
    public UserRole effectiveRoleFor(UUID userId, UserRole accountRole, UUID selectedPropertyId) {
        if (!accountRole.isPropertyScoped()) {
            return accountRole;
        }
        if (selectedPropertyId != null) {
            Optional<UserRole> assigned =
                    assignmentRepository.findRoleByUserIdAndPropertyId(userId, selectedPropertyId);
            if (assigned.isPresent()) {
                return assigned.get();
            }
            // Selected a property that isn't theirs: fall through to their
            // default rather than their account role, so a bogus header can't
            // hand a caretaker manager rights. assertCanAccess still refuses
            // the data itself.
        }
        UUID fallback = defaultPropertyFor(userId);
        if (fallback == null) {
            return accountRole;
        }
        return assignmentRepository.findRoleByUserIdAndPropertyId(userId, fallback)
                .orElse(accountRole);
    }

    /**
     * A scoped user's default active property when none is explicitly selected:
     * the oldest property assigned to them (matching the property list order),
     * or {@code null} if they have no assignments.
     */
    public UUID defaultPropertyFor(UUID userId) {
        return assignmentRepository.findAssignedPropertyIdsOrdered(userId)
                .stream()
                .findFirst()
                .orElse(null);
    }
}
