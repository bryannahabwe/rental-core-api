package com.cognix.rentalcoreapi.shared.security;

import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import com.cognix.rentalcoreapi.modules.users.repository.UserPropertyAssignmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PropertyAccessGuardTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID KIREKA = UUID.randomUUID();
    private static final UUID NTINDA = UUID.randomUUID();
    private static final UUID SOMEONE_ELSES = UUID.randomUUID();

    @Mock
    private UserPropertyAssignmentRepository assignmentRepository;

    @InjectMocks
    private PropertyAccessGuard guard;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        PropertyContextHolder.clear();
    }

    // ── effectiveRoleFor ──────────────────────────────────────────────────

    @Test
    void accountWideRoleIgnoresTheActiveProperty() {
        // ACCOUNTANT (and SUPER_ADMIN) stay account-wide; ADMIN is now scoped.
        assertThat(guard.effectiveRoleFor(USER, UserRole.ACCOUNTANT, KIREKA))
                .isEqualTo(UserRole.ACCOUNTANT);
        assertThat(guard.effectiveRoleFor(USER, UserRole.ACCOUNTANT, null))
                .isEqualTo(UserRole.ACCOUNTANT);

        // Account-wide roles must not cost a lookup on every request.
        verify(assignmentRepository, never()).findRoleByUserIdAndPropertyId(any(), any());
    }

    @Test
    void scopedAdminTakesTheAdminRoleAtItsAssignedProperty() {
        when(assignmentRepository.findRoleByUserIdAndPropertyId(USER, KIREKA))
                .thenReturn(Optional.of(UserRole.ADMIN));

        assertThat(guard.effectiveRoleFor(USER, UserRole.ADMIN, KIREKA))
                .isEqualTo(UserRole.ADMIN);
    }

    @Test
    void scopedStaffTakeTheRoleAssignedAtTheSelectedProperty() {
        when(assignmentRepository.findRoleByUserIdAndPropertyId(USER, NTINDA))
                .thenReturn(Optional.of(UserRole.CARETAKER));

        assertThat(guard.effectiveRoleFor(USER, UserRole.PROPERTY_MANAGER, NTINDA))
                .isEqualTo(UserRole.CARETAKER);
    }

    @Test
    void selectingAnUnassignedPropertyFallsBackToTheDefaultNotTheAccountRole() {
        when(assignmentRepository.findRoleByUserIdAndPropertyId(USER, SOMEONE_ELSES))
                .thenReturn(Optional.empty());
        when(assignmentRepository.findAssignedPropertyIdsOrdered(USER))
                .thenReturn(List.of(NTINDA));
        when(assignmentRepository.findRoleByUserIdAndPropertyId(USER, NTINDA))
                .thenReturn(Optional.of(UserRole.CARETAKER));

        // A bogus X-Property-Id must not promote a caretaker to their (broader)
        // account role for the duration of the request.
        assertThat(guard.effectiveRoleFor(USER, UserRole.PROPERTY_MANAGER, SOMEONE_ELSES))
                .isEqualTo(UserRole.CARETAKER);
    }

    @Test
    void noSelectedPropertyTakesTheRoleAtTheDefaultProperty() {
        when(assignmentRepository.findAssignedPropertyIdsOrdered(USER))
                .thenReturn(List.of(KIREKA, NTINDA));
        when(assignmentRepository.findRoleByUserIdAndPropertyId(USER, KIREKA))
                .thenReturn(Optional.of(UserRole.PROPERTY_MANAGER));

        assertThat(guard.effectiveRoleFor(USER, UserRole.PROPERTY_MANAGER, null))
                .isEqualTo(UserRole.PROPERTY_MANAGER);
    }

    @Test
    void noAssignmentsAtAllFallsBackToTheAccountRole() {
        when(assignmentRepository.findAssignedPropertyIdsOrdered(USER)).thenReturn(List.of());

        assertThat(guard.effectiveRoleFor(USER, UserRole.CARETAKER, null))
                .isEqualTo(UserRole.CARETAKER);
    }

    // ── assertCanAccess ───────────────────────────────────────────────────

    @Test
    void caretakerIsRestrictedToAssignedPropertiesJustLikeAManager() {
        authenticateAs(UserRole.CARETAKER);
        when(assignmentRepository.existsByUserIdAndPropertyId(USER, KIREKA)).thenReturn(true);
        when(assignmentRepository.existsByUserIdAndPropertyId(USER, SOMEONE_ELSES))
                .thenReturn(false);

        assertThatCode(() -> guard.assertCanAccess(KIREKA)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.assertCanAccess(SOMEONE_ELSES))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void accountantPassesStraightThroughTheGuard() {
        authenticateAs(UserRole.ACCOUNTANT);

        assertThatCode(() -> guard.assertCanAccess(SOMEONE_ELSES)).doesNotThrowAnyException();
        assertThat(guard.requireAccessibleProperty()).isNull(); // the all-properties aggregate
    }

    @Test
    void scopedAdminIsRestrictedToAssignedPropertiesLikeAManager() {
        authenticateAs(UserRole.ADMIN);
        when(assignmentRepository.existsByUserIdAndPropertyId(USER, KIREKA)).thenReturn(true);
        when(assignmentRepository.existsByUserIdAndPropertyId(USER, SOMEONE_ELSES))
                .thenReturn(false);

        assertThatCode(() -> guard.assertCanAccess(KIREKA)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.assertCanAccess(SOMEONE_ELSES))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void supportSessionReadsAccountWideDespiteScopedAdminRole() {
        authenticateAsSupport(UserRole.ADMIN);

        // No assignment rows, ADMIN is scoped — but support bypasses the guard.
        assertThatCode(() -> guard.assertCanAccess(SOMEONE_ELSES)).doesNotThrowAnyException();
        assertThat(guard.requireAccessibleProperty()).isNull(); // all-properties aggregate
    }

    private void authenticateAs(UserRole role) {
        authenticate(new AuthenticatedUser(
                UUID.randomUUID(), USER, role, "Test User", "+256700000000"));
    }

    private void authenticateAsSupport(UserRole role) {
        authenticate(new AuthenticatedUser(
                UUID.randomUUID(), USER, role, "Support Staff", "support@example.com",
                UUID.randomUUID()));
    }

    private void authenticate(AuthenticatedUser principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
    }
}
