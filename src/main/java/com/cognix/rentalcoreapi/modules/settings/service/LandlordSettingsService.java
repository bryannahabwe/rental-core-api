package com.cognix.rentalcoreapi.modules.settings.service;

import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.settings.dto.LandlordSettingsRequest;
import com.cognix.rentalcoreapi.modules.settings.dto.LandlordSettingsResponse;
import com.cognix.rentalcoreapi.modules.settings.model.LandlordSettings;
import com.cognix.rentalcoreapi.modules.settings.repository.LandlordSettingsRepository;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import com.cognix.rentalcoreapi.shared.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LandlordSettingsService {

    private final LandlordSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    /**
     * Get settings — auto-creates defaults if none exist yet.
     */
    public LandlordSettingsResponse getSettings() {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        LandlordSettings settings = settingsRepository
                .findByLandlordId(landlordId)
                .orElseGet(() -> createDefaults(landlordId));
        return LandlordSettingsResponse.from(settings);
    }

    /**
     * Update text settings fields.
     */
    @Transactional
    public LandlordSettingsResponse updateSettings(LandlordSettingsRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        LandlordSettings settings = settingsRepository
                .findByLandlordId(landlordId)
                .orElseGet(() -> createDefaults(landlordId));

        if (request.companyName()    != null) settings.setCompanyName(request.companyName());
        if (request.address()        != null) settings.setAddress(request.address());
        if (request.receiptPrefix()  != null) settings.setReceiptPrefix(request.receiptPrefix());
        if (request.nextReceiptNo()  != null) settings.setNextReceiptNo(request.nextReceiptNo());
        if (request.receiptNumbering() != null) settings.setReceiptNumbering(request.receiptNumbering());
        if (request.receiptFooter()  != null) settings.setReceiptFooter(request.receiptFooter());
        if (request.receiptStyle()   != null) settings.setReceiptStyle(request.receiptStyle());

        return LandlordSettingsResponse.from(settingsRepository.save(settings));
    }

    /**
     * Upload logo to Cloudinary and store URL.
     */
    @Transactional
    public LandlordSettingsResponse uploadLogo(MultipartFile file) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        LandlordSettings settings = settingsRepository
                .findByLandlordId(landlordId)
                .orElseGet(() -> createDefaults(landlordId));

        // Use landlordId as public_id so re-upload overwrites previous logo
        String publicId = landlordId.toString();
        String logoUrl = fileStorageService.upload(file, "landlord/logo", publicId);

        settings.setLogoUrl(logoUrl);
        return LandlordSettingsResponse.from(settingsRepository.save(settings));
    }

    /**
     * Increment and return the next receipt number.
     * Called when a receipt is generated.
     */
    @Transactional
    public String getNextReceiptNumber() {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        LandlordSettings settings = settingsRepository
                .findByLandlordId(landlordId)
                .orElseGet(() -> createDefaults(landlordId));

        int number = settings.getNextReceiptNo();
        String formatted = settings.getReceiptPrefix() + "-" +
                String.format("%03d", number);

        // Increment for next time
        settings.setNextReceiptNo(number + 1);
        settingsRepository.save(settings);

        return formatted;
    }

    // ── Private ──────────────────────────────────────────

    private LandlordSettings createDefaults(UUID landlordId) {
        LandlordSettings defaults = LandlordSettings.builder()
                .landlord(userRepository.getReferenceById(landlordId))
                .build();
        return settingsRepository.save(defaults);
    }
}