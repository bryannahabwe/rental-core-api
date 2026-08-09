package com.cognix.rentalcoreapi.modules.platform.service;

import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.service.AuditWriter;
import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.platform.dto.*;
import com.cognix.rentalcoreapi.modules.platform.model.PlatformUser;
import com.cognix.rentalcoreapi.modules.platform.model.SupportSession;
import com.cognix.rentalcoreapi.modules.platform.repository.PlatformUserRepository;
import com.cognix.rentalcoreapi.modules.platform.repository.SupportSessionRepository;
import com.cognix.rentalcoreapi.modules.properties.repository.PropertyRepository;
import com.cognix.rentalcoreapi.shared.exception.ConflictException;
import com.cognix.rentalcoreapi.shared.exception.NotFoundException;
import com.cognix.rentalcoreapi.shared.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Cognix staff authentication, and their time-boxed read-only access to a
 * customer account.
 *
 * <p>Two deliberate steps: signing in proves who the staff member is and grants
 * nothing; opening a session names an account and a reason and grants read-only
 * access to that one account until it expires or is ended.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformSupportService {

    private final PlatformUserRepository platformUserRepository;
    private final SupportSessionRepository supportSessionRepository;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditWriter auditWriter;

    @Value("${app.support.platform-token-expiry-ms:3600000}")
    private long platformTokenExpiryMs;

    @Value("${app.support.default-session-minutes:30}")
    private int defaultSessionMinutes;

    @Value("${app.support.max-session-minutes:120}")
    private int maxSessionMinutes;

    /**
     * Signs in a member of platform staff.
     *
     * <p>Deliberately does not use {@code AuthenticationManager}: that is wired
     * to a {@link org.springframework.security.core.userdetails.UserDetailsService}
     * over the customer {@code users} table, which is the wrong store and must
     * stay that way.
     */
    public PlatformLoginResponse login(PlatformLoginRequest request) {
        PlatformUser staff = platformUserRepository.findByEmailIgnoreCase(request.email())
                .filter(PlatformUser::isActive)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> {
                    // One message for "no such account", "deactivated" and "wrong
                    // password" — don't confirm which platform emails exist.
                    log.warn("Rejected platform sign-in for {}", request.email());
                    return new AccessDeniedException("Invalid credentials");
                });

        return new PlatformLoginResponse(
                jwtService.generatePlatformToken(staff.getId(), staff.getEmail(), platformTokenExpiryMs),
                staff.getId(), staff.getName(), staff.getEmail());
    }

    /** Customer accounts, for choosing which to support. Carries no customer
     *  business data — see {@link UserRepository#searchAccountOwners}. */
    public List<AccountSummaryResponse> searchAccounts(String query) {
        String q = (query == null || query.isBlank()) ? null : query.trim();
        return userRepository.searchAccountOwners(q).stream()
                .map(owner -> new AccountSummaryResponse(
                        owner.getId(),
                        owner.getName(),
                        owner.getEmail(),
                        owner.getPhoneNumber(),
                        userRepository.countByAccountOwnerId(owner.getId()),
                        propertyRepository.countByLandlordId(owner.getId()),
                        owner.getCreatedAt()))
                .toList();
    }

    /**
     * Opens a read-only session against one account and returns the token that
     * carries it.
     *
     * <p>The audit row is written against the <em>customer's</em> account, so it
     * lands in their own activity feed: they can see that support looked, when,
     * and why, without having to ask. The audit table's immutability trigger
     * means it cannot later be removed.
     */
    @Transactional
    public SupportSessionResponse startSession(UUID platformUserId, StartSupportSessionRequest request) {
        PlatformUser staff = activeStaff(platformUserId);

        User owner = userRepository.findById(request.accountId())
                .filter(u -> u.getId().equals(u.getAccountOwnerId()))
                .orElseThrow(() -> new NotFoundException(
                        "No such account — the id must be an account owner's user id"));

        int minutes = Math.min(
                request.ttlMinutes() == null ? defaultSessionMinutes : request.ttlMinutes(),
                maxSessionMinutes);

        SupportSession session = supportSessionRepository.save(SupportSession.builder()
                .platformUserId(staff.getId())
                .accountId(owner.getId())
                .reason(request.reason().trim())
                .expiresAt(LocalDateTime.now().plusMinutes(minutes))
                .build());

        auditWriter.record(AuditModule.AUTHENTICATION, AuditAction.SUPPORT_ACCESS_START,
                owner.getId(), staff.getId(), actorName(staff), null, session.getId().toString(),
                "Cognix Support (%s) opened a read-only support session for %d minutes — reason: %s."
                        .formatted(staff.getName(), minutes, session.getReason()));

        log.info("Support session {} opened by {} on account {}",
                session.getId(), staff.getEmail(), owner.getId());

        String token = jwtService.generateSupportToken(
                session.getId(), staff.getEmail(), Duration.ofMinutes(minutes).toMillis());

        return SupportSessionResponse.of(session, owner.getName(), staff.getName(),
                token, LocalDateTime.now());
    }

    /**
     * Ends a session. Access dies on the next request because the filter re-reads
     * the session rather than trusting the token, so no revocation list is needed.
     */
    @Transactional
    public SupportSessionResponse endSession(UUID platformUserId, UUID sessionId) {
        PlatformUser staff = activeStaff(platformUserId);

        SupportSession session = supportSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Support session not found"));

        if (!session.getPlatformUserId().equals(staff.getId())) {
            throw new AccessDeniedException("You can only end your own support sessions");
        }
        if (session.getEndedAt() != null) {
            throw new ConflictException("This session has already ended");
        }

        session.setEndedAt(LocalDateTime.now());
        supportSessionRepository.save(session);

        auditWriter.record(AuditModule.AUTHENTICATION, AuditAction.SUPPORT_ACCESS_END,
                session.getAccountId(), staff.getId(), actorName(staff), null,
                session.getId().toString(),
                "Cognix Support (%s) ended the support session.".formatted(staff.getName()));

        return toResponse(session, LocalDateTime.now());
    }

    /** Session history, for platform staff. Newest first. */
    public List<SupportSessionResponse> listSessions(UUID accountId) {
        LocalDateTime now = LocalDateTime.now();
        List<SupportSession> sessions = accountId == null
                ? supportSessionRepository.findAllByOrderByCreatedAtDesc()
                : supportSessionRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId);
        return sessions.stream().map(s -> toResponse(s, now)).toList();
    }

    private PlatformUser activeStaff(UUID platformUserId) {
        return platformUserRepository.findById(platformUserId)
                .filter(PlatformUser::isActive)
                .orElseThrow(() -> new AccessDeniedException("Platform account is not active"));
    }

    /** The name every audit sentence is attributed to, matching what the auth
     *  filter puts on the principal during a live session. */
    private static String actorName(PlatformUser staff) {
        return "Cognix Support (%s)".formatted(staff.getName());
    }

    /** Never echoes a token — one is only ever returned at session start. */
    private SupportSessionResponse toResponse(SupportSession session, LocalDateTime now) {
        String accountName = userRepository.findById(session.getAccountId())
                .map(User::getName).orElse("(unknown account)");
        String staffName = platformUserRepository.findById(session.getPlatformUserId())
                .map(PlatformUser::getName).orElse("(unknown)");
        return SupportSessionResponse.of(session, accountName, staffName, null, now);
    }
}
