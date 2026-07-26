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

@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class LandlordSettingsController {

    private final LandlordSettingsService settingsService;

    @GetMapping
    public ResponseEntity<LandlordSettingsResponse> getSettings() {
        return ResponseEntity.ok(settingsService.getSettings());
    }

    @PutMapping
    public ResponseEntity<LandlordSettingsResponse> updateSettings(
            @RequestBody LandlordSettingsRequest request) {
        return ResponseEntity.ok(settingsService.updateSettings(request));
    }

    @PostMapping(value = "/logo",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LandlordSettingsResponse> uploadLogo(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(settingsService.uploadLogo(file));
    }

    @PostMapping("/receipt-number")
    public ResponseEntity<String> getNextReceiptNumber() {
        return ResponseEntity.ok(settingsService.getNextReceiptNumber());
    }
}