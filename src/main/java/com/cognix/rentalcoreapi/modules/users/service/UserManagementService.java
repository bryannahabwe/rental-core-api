package com.cognix.rentalcoreapi.modules.users.service;

import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import com.cognix.rentalcoreapi.modules.auth.model.UserStatus;
import com.cognix.rentalcoreapi.modules.audit.AuditDiff;
import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.service.AuditWriter;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.notification.EmailService;
import com.cognix.rentalcoreapi.modules.properties.model.Property;
import com.cognix.rentalcoreapi.modules.properties.repository.PropertyRepository;
import com.cognix.rentalcoreapi.modules.users.dto.InviteUserRequest;
import com.cognix.rentalcoreapi.modules.users.dto.PropertyAssignmentRequest;
import com.cognix.rentalcoreapi.modules.users.dto.UpdateProfileRequest;
import com.cognix.rentalcoreapi.modules.users.dto.UpdateUserRequest;
import com.cognix.rentalcoreapi.modules.users.dto.UserResponse;
import com.cognix.rentalcoreapi.modules.users.model.UserPropertyAssignment;
import com.cognix.rentalcoreapi.modules.users.repository.UserPropertyAssignmentRepository;
import com.cognix.rentalcoreapi.shared.security.JwtService;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final UserPropertyAssignmentRepository assignmentRepository;
    private final PropertyRepository propertyRepository;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final AuditWriter auditWriter;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public List<UserResponse> listUsers() {
        UUID account = JwtUtils.getCurrentLandlordId();
        return userRepository.findAllByAccountOwnerIdOrderByCreatedAtAsc(account)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public UserResponse getMe() {
        // A support session has no row in `users` — the actor is Cognix staff,
        // not a member of this account. Returning a synthetic profile keeps the
        // clients that call /users/me on every load working, and is why a
        // support session can drive the ordinary app at all.
        if (JwtUtils.isSupportSession()) {
            return supportProfile();
        }
        User me = userRepository.findById(JwtUtils.getCurrentUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return toResponse(me);
    }

    /** What {@code /users/me} reports during a support session. Presents the
     *  staff member honestly rather than impersonating anyone in the account. */
    private static UserResponse supportProfile() {
        return new UserResponse(
                JwtUtils.getCurrentUserId(),
                JwtUtils.getCurrentUserName(),
                null,
                null,
                UserRole.ADMIN,
                UserStatus.ACTIVE,
                List.of(),
                Map.of(),
                null);
    }

    /** Lets any user update their own name and phone number. */
    @Transactional
    public UserResponse updateMe(UpdateProfileRequest request) {
        if (JwtUtils.isSupportSession()) {
            // Belt and braces — SupportReadOnlyFilter already refuses the PUT.
            throw new AccessDeniedException("Support sessions have no profile to edit");
        }
        User me = userRepository.findById(JwtUtils.getCurrentUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Reject a phone number already taken by a different user.
        userRepository.findByPhoneNumber(request.phoneNumber())
                .filter(other -> !other.getId().equals(me.getId()))
                .ifPresent(other -> {
                    throw new IllegalArgumentException(
                            "A user with this phone number already exists: " + request.phoneNumber());
                });

        List<String> changes = new ArrayList<>();
        AuditDiff.diff(changes, "name", me.getName(), request.name());
        AuditDiff.diff(changes, "phone", me.getPhoneNumber(), request.phoneNumber());

        me.setName(request.name());
        me.setPhoneNumber(request.phoneNumber());
        userRepository.save(me);

        if (!changes.isEmpty()) {
            auditWriter.record(AuditModule.USER, AuditAction.PROFILE_UPDATE, null, me.getName(),
                    "%s updated their profile: %s.".formatted(
                            me.getName(), String.join("; ", changes)));
        }

        return toResponse(me);
    }

    @Transactional
    public UserResponse inviteUser(InviteUserRequest request) {
        UUID account = JwtUtils.getCurrentLandlordId();
        List<PropertyAssignmentRequest> assignments =
                normalise(request.role(), request.assignments(), request.propertyIds());
        UserRole accountRole = accountRoleFor(request.role(), assignments);
        assertCanAssignRoles(accountRole, assignments);

        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException(
                    "A user with this email already exists: " + request.email());
        }

        if (userRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new IllegalArgumentException(
                    "A user with this phone number already exists: " + request.phoneNumber());
        }

        User user = User.builder()
                .name(request.name())
                .phoneNumber(request.phoneNumber())
                .email(request.email())
                .role(accountRole)
                .status(UserStatus.INVITED)
                .accountOwnerId(account)
                .build();
        userRepository.save(user);

        replaceAssignments(user, accountRole, assignments, account);

        sendInviteEmail(user, account);

        auditWriter.record(AuditModule.USER, AuditAction.INVITE, null, user.getEmail(),
                "%s invited %s as %s with access to %s.".formatted(
                        JwtUtils.getCurrentUserName(), user.getEmail(), labelFor(accountRole),
                        describeAssignments(accountRole, user.getId())));

        return toResponse(user);
    }

    /**
     * Re-issues a fresh invite token and re-sends the invitation email for a
     * user who hasn't accepted yet (e.g. the original link expired or was lost).
     */
    @Transactional
    public UserResponse resendInvite(UUID id) {
        UUID account = JwtUtils.getCurrentLandlordId();
        User user = loadManageableUser(id, account);

        if (user.getStatus() != UserStatus.INVITED) {
            throw new IllegalArgumentException(user.getStatus() == UserStatus.ACTIVE
                    ? "This user has already accepted their invitation"
                    : "Cannot resend an invitation to a deactivated user");
        }

        sendInviteEmail(user, account);

        auditWriter.record(AuditModule.USER, AuditAction.RESEND_INVITE, null, user.getEmail(),
                "%s re-sent the invitation to %s.".formatted(
                        JwtUtils.getCurrentUserName(), user.getEmail()));

        return toResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        UUID account = JwtUtils.getCurrentLandlordId();
        User user = loadManageableUser(id, account);
        List<PropertyAssignmentRequest> assignments =
                normalise(request.role(), request.assignments(), request.propertyIds());
        UserRole accountRole = accountRoleFor(request.role(), assignments);
        assertCanAssignRoles(accountRole, assignments);

        // Reject a phone number already taken by a different user.
        userRepository.findByPhoneNumber(request.phoneNumber())
                .filter(other -> !other.getId().equals(user.getId()))
                .ifPresent(other -> {
                    throw new IllegalArgumentException(
                            "A user with this phone number already exists: " + request.phoneNumber());
                });

        // Capture what changed before the setters run — the property assignments
        // included, since which properties someone can reach is a permissions
        // change and belongs in the trail as plainly as the role.
        UserRole previousRole = user.getRole();
        List<String> changes = new ArrayList<>();
        AuditDiff.diff(changes, "role", labelFor(previousRole), labelFor(accountRole));
        AuditDiff.diff(changes, "phone", user.getPhoneNumber(), request.phoneNumber());
        String assignmentsBefore = describeAssignments(previousRole, user.getId());
        Map<UUID, UserRole> rolesBefore = assignedRolesFor(user);

        user.setRole(accountRole);
        user.setPhoneNumber(request.phoneNumber());
        userRepository.save(user);

        replaceAssignments(user, accountRole, assignments, account);

        AuditDiff.diff(changes, "property access",
                assignmentsBefore, describeAssignments(accountRole, user.getId()));

        if (!changes.isEmpty()) {
            // A role change is the material event for a permissions audit;
            // anything else is a plain edit. With per-property roles the account
            // role can stay put while someone is demoted at one property, so
            // that counts too — otherwise the demotion would be filed as a
            // plain UPDATE and miss an audit filtered on ROLE_CHANGE.
            boolean roleChanged = previousRole != accountRole
                    || roleChangedAtSomeProperty(rolesBefore, assignedRolesFor(user));
            AuditAction action = roleChanged ? AuditAction.ROLE_CHANGE : AuditAction.UPDATE;
            auditWriter.record(AuditModule.USER, action, null, user.getName(),
                    "%s updated user %s: %s.".formatted(
                            JwtUtils.getCurrentUserName(), user.getName(),
                            String.join("; ", changes)));
        }

        return toResponse(user);
    }

    @Transactional
    public void deactivateUser(UUID id) {
        UUID account = JwtUtils.getCurrentLandlordId();
        User user = loadManageableUser(id, account);

        if (user.getId().equals(JwtUtils.getCurrentUserId())) {
            throw new IllegalArgumentException("You cannot deactivate your own account");
        }
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("The account owner cannot be deactivated");
        }

        user.setStatus(UserStatus.DEACTIVATED);
        userRepository.save(user);

        auditWriter.record(AuditModule.USER, AuditAction.DEACTIVATE, null, user.getName(),
                "%s deactivated %s.".formatted(JwtUtils.getCurrentUserName(), user.getName()));
    }

    // ── Guards ────────────────────────────────────────────────────────────

    /**
     * Folds the request's assignments into a single shape. The per-property
     * {@code assignments} win when present; otherwise the legacy
     * {@code propertyIds} are taken at the request's top-level role, which is
     * how every client behaved before roles became per-property.
     */
    private static List<PropertyAssignmentRequest> normalise(
            UserRole role, List<PropertyAssignmentRequest> assignments, List<UUID> propertyIds) {
        if (assignments != null && !assignments.isEmpty()) {
            return assignments;
        }
        if (!role.isPropertyScoped() || propertyIds == null) {
            return List.of();
        }
        return propertyIds.stream()
                .filter(Objects::nonNull)
                .map(propertyId -> new PropertyAssignmentRequest(propertyId, role))
                .toList();
    }

    /**
     * The role stored on the user row. Account-wide roles are taken as asked
     * for; for scoped staff it is derived from the assignments, so the column
     * can't disagree with what the user actually holds somewhere. Derived rather
     * than compared by ordinal — declaration order is not a privilege lattice.
     */
    private static UserRole accountRoleFor(UserRole requested,
                                           List<PropertyAssignmentRequest> assignments) {
        if (!requested.isPropertyScoped()) {
            return requested;
        }
        return assignments.stream()
                .anyMatch(assignment -> assignment.role() == UserRole.PROPERTY_MANAGER)
                ? UserRole.PROPERTY_MANAGER
                : UserRole.CARETAKER;
    }

    /** Checks the caller may hand out the account role and every role they're
     *  assigning at a property. */
    private void assertCanAssignRoles(UserRole accountRole,
                                      List<PropertyAssignmentRequest> assignments) {
        assertCanAssignRole(accountRole);
        assignments.stream()
                .map(PropertyAssignmentRequest::role)
                .distinct()
                .forEach(this::assertCanAssignRole);
    }

    /** A caller may never create/assign the SUPER_ADMIN role; ADMINs may manage
     *  every role except another admin. */
    private void assertCanAssignRole(UserRole targetRole) {
        if (targetRole == UserRole.SUPER_ADMIN) {
            throw new AccessDeniedException("The owner role cannot be assigned");
        }
        if (JwtUtils.getCurrentRole() == UserRole.ADMIN && targetRole.isAdmin()) {
            throw new AccessDeniedException("Admins cannot manage other admins");
        }
    }

    /** Loads a user in the caller's account and checks the caller outranks them. */
    private User loadManageableUser(UUID id, UUID account) {
        User user = userRepository.findByIdAndAccountOwnerId(id, account)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (JwtUtils.getCurrentRole() == UserRole.ADMIN && user.getRole().isAdmin()) {
            throw new AccessDeniedException("Admins can only manage property managers");
        }
        return user;
    }

    /** Rotates the invite token and schedules the branded invitation email. */
    private void sendInviteEmail(User user, UUID account) {
        // Rotate the token version so any previously issued link is invalidated
        // — only the newest invitation can be accepted. This must be persisted
        // with the surrounding transaction so the emailed link is valid.
        UUID tokenVersion = UUID.randomUUID();
        user.setInviteTokenVersion(tokenVersion);
        userRepository.save(user);

        // Invite link → the public accept-invite page.
        String token = jwtService.generateInviteToken(user.getId(), user.getEmail(), tokenVersion);
        String acceptUrl = frontendUrl + "/accept-invite?token=" + token;
        String accountName = userRepository.findById(account).map(User::getName).orElse("RentFlow");
        String recipient = user.getEmail();
        String name = user.getName();

        // Send only after the transaction commits, so an email-provider outage
        // can't roll back the invited user. Best-effort: failures are logged and
        // the admin can use "Resend invite".
        runAfterCommit(() -> {
            try {
                emailService.sendInvite(recipient, name, accountName, acceptUrl);
            } catch (Exception e) {
                log.warn("Invite email to {} failed to send: {}", recipient, e.getMessage());
            }
        });
    }

    /**
     * Runs an action once the current transaction commits, or immediately if no
     * transaction is active (e.g. in tests).
     */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void replaceAssignments(User user, UserRole role,
                                    List<PropertyAssignmentRequest> assignments, UUID account) {
        if (!role.isPropertyScoped()) {
            // Account-wide roles reach everything; drop any stale assignments.
            assignmentRepository.deleteByUserId(user.getId());
            return;
        }
        // Scoped staff with nothing assigned can access nothing — reject it up
        // front rather than creating a user who lands on an empty account.
        if (assignments.isEmpty()) {
            throw new IllegalArgumentException(
                    "A %s must be assigned at least one property".formatted(
                            labelFor(role).toLowerCase()));
        }
        assignmentRepository.deleteByUserId(user.getId());
        // Force the deletes to hit the DB before the re-inserts below. Hibernate
        // otherwise orders inserts ahead of deletes at flush, so re-assigning a
        // property the user already had collides with the (user, property)
        // unique constraint.
        assignmentRepository.flush();
        Set<UUID> assigned = new HashSet<>();
        for (PropertyAssignmentRequest assignment : assignments) {
            if (!assignment.role().isPropertyScoped()) {
                throw new IllegalArgumentException(
                        "%s reaches every property and cannot be assigned to one".formatted(
                                labelFor(assignment.role())));
            }
            // First mention of a property wins, matching the de-duplication the
            // legacy propertyIds list got.
            if (!assigned.add(assignment.propertyId())) {
                continue;
            }
            if (!propertyRepository.existsByIdAndLandlordId(assignment.propertyId(), account)) {
                throw new IllegalArgumentException("Property not found: " + assignment.propertyId());
            }
            assignmentRepository.save(UserPropertyAssignment.builder()
                    .userId(user.getId())
                    .propertyId(assignment.propertyId())
                    .role(assignment.role())
                    .build());
        }
    }

    /**
     * A user's property access as an audit-readable phrase: each property and
     * the role held there for scoped staff, "all properties" for account-wide
     * roles. Sorted so the before/after comparison doesn't fire on row ordering
     * alone.
     */
    private String describeAssignments(UserRole role, UUID userId) {
        if (!role.isPropertyScoped()) {
            return "all properties";
        }
        List<UserPropertyAssignment> assignments =
                assignmentRepository.findAssignmentsOrdered(userId);
        if (assignments.isEmpty()) {
            return "no properties";
        }
        Map<UUID, String> names = propertyRepository
                .findAllById(UserPropertyAssignment.propertyIds(assignments)).stream()
                .collect(Collectors.toMap(Property::getId, Property::getName));
        return assignments.stream()
                .map(assignment -> "%s (%s)".formatted(
                        names.get(assignment.getPropertyId()), labelFor(assignment.getRole())))
                .sorted()
                .collect(Collectors.joining(", "));
    }

    /**
     * True if any property the user still holds is held at a different role than
     * before — a demotion or promotion that leaves the account role untouched.
     */
    private static boolean roleChangedAtSomeProperty(Map<UUID, UserRole> before,
                                                     Map<UUID, UserRole> after) {
        return before.entrySet().stream()
                .anyMatch(entry -> after.containsKey(entry.getKey())
                        && after.get(entry.getKey()) != entry.getValue());
    }

    private Map<UUID, UserRole> assignedRolesFor(User user) {
        if (!user.getRole().isPropertyScoped()) {
            return Map.of();
        }
        return UserPropertyAssignment.rolesByProperty(
                assignmentRepository.findAssignmentsOrdered(user.getId()));
    }

    private static String labelFor(UserRole role) {
        return switch (role) {
            case SUPER_ADMIN -> "Owner";
            case ADMIN -> "Admin";
            case PROPERTY_MANAGER -> "Property Manager";
            case CARETAKER -> "Caretaker";
            case ACCOUNTANT -> "Accountant";
        };
    }

    private UserResponse toResponse(User user) {
        if (!user.getRole().isPropertyScoped()) {
            return UserResponse.from(user, List.of(), Map.of());
        }
        List<UserPropertyAssignment> assignments =
                assignmentRepository.findAssignmentsOrdered(user.getId());
        return UserResponse.from(user,
                UserPropertyAssignment.propertyIds(assignments),
                UserPropertyAssignment.rolesByProperty(assignments));
    }
}
