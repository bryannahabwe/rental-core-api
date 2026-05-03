package com.cognix.rentalcoreapi.shared.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryStorageService implements FileStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024; // 20 MB
    private static final java.util.List<String> ALLOWED_TYPES = java.util.List.of(
            "image/jpeg", "image/png", "image/webp", "image/svg+xml"
    );
    private static final String RESOURCE_TYPE = "resource_type";
    private static final String RESOURCE_TYPE_IMAGE = "image";
    private final Cloudinary cloudinary;

    @Override
    @SuppressWarnings("unchecked")
    public String upload(MultipartFile file, String folder, String publicId) {
        validate(file);

        try {
            Map<String, Object> options = publicId != null
                    ? ObjectUtils.asMap(
                    "folder", folder,
                    "public_id", publicId,
                    "overwrite", true,
                    RESOURCE_TYPE, RESOURCE_TYPE_IMAGE
            )
                    : ObjectUtils.asMap(
                    "folder", folder,
                    RESOURCE_TYPE, RESOURCE_TYPE_IMAGE
            );

            Object result = cloudinary.uploader().upload(file.getBytes(), options);

            String url = extractSecureUrl(result);
            log.info("File uploaded to Cloudinary: {}", url);
            return url;

        } catch (IOException ex) {
            log.error("Cloudinary upload failed", ex);
            throw new ValidationException("File upload failed. Please try again.");
        }
    }

    @SuppressWarnings("unchecked")
    private String extractSecureUrl(Object result) {
        Map<String, Object> resultMap = (Map<String, Object>) result;
        return (String) resultMap.get("secure_url");
    }

    @Override
    public void delete(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId,
                    ObjectUtils.asMap(RESOURCE_TYPE, RESOURCE_TYPE_IMAGE));
            log.info("File deleted from Cloudinary: {}", publicId);
        } catch (IOException ex) {
            log.warn("Cloudinary delete failed for publicId: {}", publicId, ex);
        }
    }

    // ── private ──────────────────────────────────────────────────────────────

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("No file provided");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ValidationException(
                    "File size exceeds the 5 MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new ValidationException(
                    "Invalid file type. Allowed types: JPEG, PNG, WebP, SVG");
        }
    }
}