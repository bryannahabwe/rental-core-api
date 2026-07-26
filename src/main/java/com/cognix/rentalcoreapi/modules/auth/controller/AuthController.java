package com.cognix.rentalcoreapi.modules.auth.controller;

import com.cognix.rentalcoreapi.modules.auth.dto.AcceptInviteRequest;
import com.cognix.rentalcoreapi.modules.auth.dto.AuthResponse;
import com.cognix.rentalcoreapi.modules.auth.dto.InviteInfoResponse;
import com.cognix.rentalcoreapi.modules.auth.dto.LoginRequest;
import com.cognix.rentalcoreapi.modules.auth.dto.RefreshRequest;
import com.cognix.rentalcoreapi.modules.auth.dto.RegisterRequest;
import com.cognix.rentalcoreapi.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @GetMapping("/invite/{token}")
    public ResponseEntity<InviteInfoResponse> getInvite(@PathVariable String token) {
        return ResponseEntity.ok(authService.getInvite(token));
    }

    @PostMapping("/accept-invite")
    public ResponseEntity<AuthResponse> acceptInvite(
            @Valid @RequestBody AcceptInviteRequest request) {
        return ResponseEntity.ok(authService.acceptInvite(request));
    }
}