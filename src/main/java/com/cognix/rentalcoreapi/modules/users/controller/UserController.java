package com.cognix.rentalcoreapi.modules.users.controller;

import com.cognix.rentalcoreapi.modules.users.dto.InviteUserRequest;
import com.cognix.rentalcoreapi.modules.users.dto.UpdateProfileRequest;
import com.cognix.rentalcoreapi.modules.users.dto.UpdateUserRequest;
import com.cognix.rentalcoreapi.modules.users.dto.UserResponse;
import com.cognix.rentalcoreapi.modules.users.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserManagementService userManagementService;

    // Available to any authenticated user (managers included) so the frontend
    // can configure role-aware UI.
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        return ResponseEntity.ok(userManagementService.getMe());
    }

    // Any authenticated user can edit their own name and phone number.
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userManagementService.updateMe(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<List<UserResponse>> listUsers() {
        return ResponseEntity.ok(userManagementService.listUsers());
    }

    @PostMapping("/invite")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<UserResponse> inviteUser(
            @Valid @RequestBody InviteUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userManagementService.inviteUser(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userManagementService.updateUser(id, request));
    }

    @PostMapping("/{id}/resend-invite")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<UserResponse> resendInvite(@PathVariable UUID id) {
        return ResponseEntity.ok(userManagementService.resendInvite(id));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<Void> deactivateUser(@PathVariable UUID id) {
        userManagementService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    // Only the current owner may hand ownership to another (already-admin) user.
    // The caller is demoted to ADMIN in the same step.
    @PostMapping("/{id}/transfer-ownership")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> transferOwnership(@PathVariable UUID id) {
        userManagementService.transferOwnership(id);
        return ResponseEntity.noContent().build();
    }
}
