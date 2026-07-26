package com.cognix.rentalcoreapi.modules.users.service;

import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import com.cognix.rentalcoreapi.modules.auth.model.UserStatus;
import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.service.AuditWriter;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.notification.EmailService;
import com.cognix.rentalcoreapi.modules.properties.repository.PropertyRepository;
import com.cognix.rentalcoreapi.modules.users.dto.InviteUserRequest;
import com.cognix.rentalcoreapi.modules.users.dto.UpdateUserRequest;
import com.cognix.rentalcoreapi.modules.users.dto.UserResponse;
import com.cognix.rentalcoreapi.modules.users.model.UserPropertyAssignment;
import com.cognix.rentalcoreapi.modules.users.repository.UserPropertyAssignmentRepository;
import com.cognix.rentalcoreapi.shared.security.JwtService;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
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
        User me = userRepository.findById(JwtUtils.getCurrentUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return toResponse(me);
    }

    @Transactional
    public UserResponse inviteUser(InviteUserRequest request) {
        UUID account = JwtUtils.getCurrentLandlordId();
        assertCanAssignRole(request.role());

        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException(
                    "A user with this email already exists: " + request.email());
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .role(request.role())
                .status(UserStatus.INVITED)
                .accountOwnerId(account)
                .build();
        userRepository.save(user);

        replaceAssignments(user, request.role(), request.propertyIds(), account);

        // Invite link → the public accept-invite page.
        String token = jwtService.generateInviteToken(user.getId(), user.getEmail());
        String acceptUrl = frontendUrl + "/accept-invite?token=" + token;
        String accountName = userRepository.findById(account).map(User::getName).orElse("RentFlow");
        emailService.sendInvite(user.getEmail(), user.getName(), accountName, acceptUrl);

        auditWriter.record(AuditModule.USER, AuditAction.INVITE, null, user.getEmail(),
                "%s invited %s as %s.".formatted(
                        JwtUtils.getCurrentUserName(), user.getEmail(), labelFor(request.role())));

        return toResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        UUID account = JwtUtils.getCurrentLandlordId();
        User user = loadManageableUser(id, account);
        assertCanAssignRole(request.role());

        user.setRole(request.role());
        userRepository.save(user);

        replaceAssignments(user, request.role(), request.propertyIds(), account);

        auditWriter.record(AuditModule.USER, AuditAction.ROLE_CHANGE, null, user.getName(),
                "%s updated user %s (now %s).".formatted(
                        JwtUtils.getCurrentUserName(), user.getName(), labelFor(request.role())));

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

    /** A caller may never create/assign the SUPER_ADMIN role; ADMINs may only
     *  manage PROPERTY_MANAGERs. */
    private void assertCanAssignRole(UserRole targetRole) {
        if (targetRole == UserRole.SUPER_ADMIN) {
            throw new AccessDeniedException("The owner role cannot be assigned");
        }
        if (JwtUtils.getCurrentRole() == UserRole.ADMIN
                && targetRole != UserRole.PROPERTY_MANAGER) {
            throw new AccessDeniedException("Admins can only manage property managers");
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

    private void replaceAssignments(User user, UserRole role, List<UUID> propertyIds, UUID account) {
        assignmentRepository.deleteByUserId(user.getId());
        if (role != UserRole.PROPERTY_MANAGER || propertyIds == null) {
            return;
        }
        for (UUID propertyId : propertyIds.stream().distinct().toList()) {
            if (!propertyRepository.existsByIdAndLandlordId(propertyId, account)) {
                throw new IllegalArgumentException("Property not found: " + propertyId);
            }
            assignmentRepository.save(UserPropertyAssignment.builder()
                    .userId(user.getId())
                    .propertyId(propertyId)
                    .build());
        }
    }

    private static String labelFor(UserRole role) {
        return switch (role) {
            case SUPER_ADMIN -> "Owner";
            case ADMIN -> "Admin";
            case PROPERTY_MANAGER -> "Property Manager";
        };
    }

    private UserResponse toResponse(User user) {
        List<UUID> assigned = user.getRole() == UserRole.PROPERTY_MANAGER
                ? assignmentRepository.findPropertyIdsByUserId(user.getId())
                : List.of();
        return UserResponse.from(user, assigned);
    }
}
