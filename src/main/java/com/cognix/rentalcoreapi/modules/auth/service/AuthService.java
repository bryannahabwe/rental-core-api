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
import com.cognix.rentalcoreapi.shared.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
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
        // their own account.
        User user = User.builder()
                .name(request.name())
                .phoneNumber(request.phoneNumber())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.SUPER_ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(user);
        // accountOwnerId can only be set once the generated id exists.
        user.setAccountOwnerId(user.getId());
        userRepository.save(user);

        // Every landlord starts with one default property so the app always has
        // a property to scope units/tenants under; they name it at sign-up (or
        // fall back to a placeholder) and can rename or add more later.
        String propertyName = request.propertyName() != null && !request.propertyName().isBlank()
                ? request.propertyName().trim()
                : "My Property";
        propertyRepository.save(Property.builder()
                .landlord(user)
                .name(propertyName)
                .build());

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );

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

        if (request.phone() != null && !request.phone().isBlank()) {
            String phone = request.phone().trim();
            if (userRepository.existsByPhoneNumber(phone)) {
                throw new IllegalArgumentException("Phone number already in use");
            }
            user.setPhoneNumber(phone);
        }

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

        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getId(),
                user.getName(),
                user.getPhoneNumber(),
                user.getEmail(),
                user.getRole().name()
        );
    }
}
