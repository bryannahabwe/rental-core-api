package com.cognix.rentalcoreapi.modules.settings.controller;

import com.cognix.rentalcoreapi.modules.settings.dto.LandlordSettingsRequest;
import com.cognix.rentalcoreapi.modules.settings.dto.LandlordSettingsResponse;
import com.cognix.rentalcoreapi.modules.settings.service.LandlordSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Reading settings and drawing a receipt number are gated separately from
 * editing them: anyone who records a payment needs the company name, logo and
 * receipt style to render the receipt, but changing the branding stays with the
 * office.
 */
@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class LandlordSettingsController {

    private final LandlordSettingsService settingsService;

    // Open to any authenticated user — the branding here is what a receipt is
    // rendered from, and every role that records a payment issues receipts.
    @GetMapping
    public ResponseEntity<LandlordSettingsResponse> getSettings() {
        return ResponseEntity.ok(settingsService.getSettings());
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<LandlordSettingsResponse> updateSettings(
            @RequestBody LandlordSettingsRequest request) {
        return ResponseEntity.ok(settingsService.updateSettings(request));
    }

    @PostMapping(value = "/logo",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<LandlordSettingsResponse> uploadLogo(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(settingsService.uploadLogo(file));
    }

    // Mirrors who may record a payment — a receipt number is only ever drawn as
    // part of issuing a receipt for one.
    @PostMapping("/receipt-number")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PROPERTY_MANAGER','CARETAKER')")
    public ResponseEntity<String> getNextReceiptNumber() {
        return ResponseEntity.ok(settingsService.getNextReceiptNumber());
    }
}