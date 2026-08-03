package com.cognix.rentalcoreapi.modules.auth.service;

import com.cognix.rentalcoreapi.modules.auth.dto.AcceptInviteRequest;
import com.cognix.rentalcoreapi.modules.auth.dto.AuthResponse;
import com.cognix.rentalcoreapi.modules.auth.dto.InviteInfoResponse;
import com.cognix.rentalcoreapi.modules.auth.dto.LoginRequest;
import com.cognix.rentalcoreapi.modules.auth.dto.RefreshRequest;
import com.cognix.rentalcoreapi.modules.auth.dto.RegisterRequest;
import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import com.cognix.rentalcoreapi.modules.auth.model.UserStatus;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.service.AuditWriter;
import com.cognix.rentalcoreapi.modules.properties.model.Property;
import com.cognix.rentalcoreapi.modules.properties.repository.PropertyRepository;
import com.cognix.rentalcoreapi.modules.users.model.UserPropertyAssignment;
import com.cognix.rentalcoreapi.modules.users.repository.UserPropertyAssignmentRepository;
import com.cognix.rentalcoreapi.shared.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final UserPropertyAssignmentRepository assignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AuditWriter auditWriter;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new IllegalArgumentException("Phone number already registered");
        }

        if (request.email() != null && userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered");
        }

        // The registrant is the account owner: SUPER_ADMIN, active, anchoring
        // their own account. Assign the id up front so account_owner_id can
        // reference it in the same insert — the column is a NOT NULL self-FK,
        // so the row can't be written first and back-filled afterwards.
        UUID accountId = UUID.randomUUID();
        User user = User.builder()
                .name(request.name())
                .phoneNumber(request.phoneNumber())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.SUPER_ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        user.setId(accountId);
        user.setAccountOwnerId(accountId);
        user = userRepository.save(user);

        // Every landlord starts with one default property so the app always has
        // a property to scope units/tenants under; they name it at sign-up (or
        // fall back to a placeholder) and can rename or add more later.
        String propertyName = request.propertyName() != null && !request.propertyName().isBlank()
                ? request.propertyName().trim()
                : "My Property";
        Property property = propertyRepository.save(Property.builder()
                .landlord(user)
                .name(propertyName)
                .build());

        // Explicit actor: there is no security context during registration.
        auditWriter.record(AuditModule.AUTHENTICATION, AuditAction.REGISTER,
                accountId, user.getId(), user.getName(), null, user.getUsername(),
                "%s created the account.".formatted(user.getName()));
        // The default property is created for them, so record it like any other
        // property creation — otherwise it appears in the app from nowhere.
        auditWriter.record(AuditModule.PROPERTY, AuditAction.CREATE,
                accountId, user.getId(), user.getName(), property.getId(), property.getName(),
                "%s created property %s.".formatted(user.getName(), property.getName()));

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(),
                            request.password()
                    )
            );
        } catch (AuthenticationException e) {
            recordFailedLogin(request.username());
            throw e;
        }

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        auditWriter.record(AuditModule.AUTHENTICATION, AuditAction.LOGIN,
                user.getAccountOwnerId(), user.getId(), user.getName(), null, null,
                "%s logged in.".formatted(user.getName()));

        return buildAuthResponse(user);
    }

    public AuthResponse refresh(RefreshRequest request) {
        String token = request.refreshToken();

        if (!jwtService.isTokenValid(token)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        User user = userRepository
                .findByUsername(jwtService.extractUsername(token))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return buildAuthResponse(user);
    }

    /** Public: details for the accept-invite page to display before accepting. */
    public InviteInfoResponse getInvite(String token) {
        User user = loadInvitedUser(token);
        String accountName = userRepository.findById(user.getAccountOwnerId())
                .map(User::getName)
                .orElse("RentFlow");
        return new InviteInfoResponse(user.getName(), user.getEmail(), accountName);
    }

    /** Public: consume the invite token, set a password, activate, and log in. */
    @Transactional
    public AuthResponse acceptInvite(AcceptInviteRequest request) {
        User user = loadInvitedUser(request.token());

        // The phone number is captured (and required) when the invite is sent,
        // so there's nothing to collect here — the invitee only sets a password.
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        // Consume the invite: clear the version so the link can't be replayed.
        user.setInviteTokenVersion(null);
        userRepository.save(user);

        auditWriter.record(AuditModule.AUTHENTICATION, AuditAction.ACCEPT_INVITE,
                user.getAccountOwnerId(), user.getId(), user.getName(), null, user.getEmail(),
                "%s accepted their invitation and joined.".formatted(user.getName()));

        return buildAuthResponse(user);
    }

    /**
     * Records a rejected sign-in attempt — wrong password, or an account that is
     * still invited or deactivated. Written in its own transaction because the
     * login transaction is about to roll back on the rethrown exception.
     *
     * <p>Only attempts against a known username are recorded: an audit row must
     * belong to an account to be readable, and an unrecognised username belongs
     * to none. Repeated rows for one user are the point, not noise — that pattern
     * is what makes a brute-force attempt visible.
     */
    private void recordFailedLogin(String username) {
        userRepository.findByUsername(username).ifPresent(user ->
                auditWriter.recordIndependently(
                        AuditModule.AUTHENTICATION, AuditAction.LOGIN_FAILED,
                        user.getAccountOwnerId(), user.getId(), user.getName(), null, username,
                        "Failed sign-in attempt for %s.".formatted(user.getName())));
    }

    private User loadInvitedUser(String token) {
        JwtService.InviteToken invite = jwtService.validateInviteToken(token);
        User user = userRepository.findById(invite.userId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite link"));
        if (user.getStatus() != UserStatus.INVITED) {
            throw new IllegalArgumentException("This invitation has already been accepted");
        }
        // Reject links superseded by a resend (or already consumed): only the
        // user's current token version is valid.
        if (user.getInviteTokenVersion() == null
                || !user.getInviteTokenVersion().equals(invite.version())) {
            throw new IllegalArgumentException(
                    "This invite link is no longer valid — a newer invitation has been sent");
        }
        return user;
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getUsername());

        // Scoped staff are limited to specific properties; hand the client the
        // list, the role held at each, and a default so it can activate one on
        // login instead of landing on the "All properties" view they have no
        // access to. Account-wide roles are unrestricted, so they get no default.
        List<UserPropertyAssignment> assignments = user.getRole().isPropertyScoped()
                ? assignmentRepository.findAssignmentsOrdered(user.getId())
                : List.of();
        List<UUID> assignedPropertyIds = UserPropertyAssignment.propertyIds(assignments);
        UUID defaultPropertyId = assignedPropertyIds.isEmpty()
                ? null
                : assignedPropertyIds.get(0);

        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getId(),
                user.getName(),
                user.getPhoneNumber(),
                user.getEmail(),
                user.getRole().name(),
                assignedPropertyIds,
                UserPropertyAssignment.rolesByProperty(assignments),
                defaultPropertyId
        );
    }
}
