package com.cognix.rentalcoreapi.modules.platform.controller;

import com.cognix.rentalcoreapi.modules.platform.dto.PlatformLoginRequest;
import com.cognix.rentalcoreapi.modules.platform.dto.PlatformLoginResponse;
import com.cognix.rentalcoreapi.modules.platform.service.PlatformSupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cognix staff sign-in. The only public endpoint under {@code /platform}. */
@RestController
@RequestMapping("/platform/auth")
@RequiredArgsConstructor
public class PlatformAuthController {

    private final PlatformSupportService platformSupportService;

    @PostMapping("/login")
    public ResponseEntity<PlatformLoginResponse> login(
            @Valid @RequestBody PlatformLoginRequest request) {
        return ResponseEntity.ok(platformSupportService.login(request));
    }
}
