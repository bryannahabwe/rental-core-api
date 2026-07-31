package com.cognix.rentalcoreapi.modules.settings.service;

import com.cognix.rentalcoreapi.modules.audit.AuditDiff;
import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.service.AuditWriter;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LandlordSettingsService {

    private final LandlordSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final AuditWriter auditWriter;

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

        // Absent fields mean "leave alone", so each one is diffed only when the
        // request actually carries a value.
        List<String> changes = new ArrayList<>();
        if (request.companyName() != null) {
            AuditDiff.diff(changes, "company name", settings.getCompanyName(), request.companyName());
            settings.setCompanyName(request.companyName());
        }
        if (request.address() != null) {
            AuditDiff.diff(changes, "address", settings.getAddress(), request.address());
            settings.setAddress(request.address());
        }
        if (request.receiptPrefix() != null) {
            AuditDiff.diff(changes, "receipt prefix", settings.getReceiptPrefix(), request.receiptPrefix());
            settings.setReceiptPrefix(request.receiptPrefix());
        }
        if (request.nextReceiptNo() != null) {
            AuditDiff.diff(changes, "next receipt no.", settings.getNextReceiptNo(), request.nextReceiptNo());
            settings.setNextReceiptNo(request.nextReceiptNo());
        }
        if (request.receiptNumbering() != null) {
            AuditDiff.diff(changes, "receipt numbering", settings.getReceiptNumbering(), request.receiptNumbering());
            settings.setReceiptNumbering(request.receiptNumbering());
        }
        if (request.receiptFooter() != null) {
            AuditDiff.diff(changes, "receipt footer", settings.getReceiptFooter(), request.receiptFooter());
            settings.setReceiptFooter(request.receiptFooter());
        }
        if (request.receiptStyle() != null) {
            AuditDiff.diff(changes, "receipt style", settings.getReceiptStyle(), request.receiptStyle());
            settings.setReceiptStyle(request.receiptStyle());
        }

        LandlordSettings saved = settingsRepository.save(settings);

        if (!changes.isEmpty()) {
            auditWriter.record(AuditModule.SETTINGS, AuditAction.UPDATE, null, null,
                    "%s updated account settings: %s.".formatted(
                            JwtUtils.getCurrentUserName(), String.join("; ", changes)));
        }

        return LandlordSettingsResponse.from(saved);
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
        LandlordSettings saved = settingsRepository.save(settings);

        auditWriter.record(AuditModule.SETTINGS, AuditAction.UPDATE, null, null,
                "%s updated the company logo.".formatted(JwtUtils.getCurrentUserName()));

        return LandlordSettingsResponse.from(saved);
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

        // Every issued number is recorded so the receipt sequence can be
        // reconciled — a gap or a re-issue has someone's name against it.
        auditWriter.record(AuditModule.SETTINGS, AuditAction.ISSUE_RECEIPT, null, formatted,
                "%s issued receipt number %s.".formatted(JwtUtils.getCurrentUserName(), formatted));

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