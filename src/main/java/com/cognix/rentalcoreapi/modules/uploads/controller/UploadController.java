package com.cognix.rentalcoreapi.modules.uploads.controller;

import com.cognix.rentalcoreapi.modules.uploads.dto.UploadResponse;
import com.cognix.rentalcoreapi.shared.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * Generic file upload → returns the stored file's public URL. Callers persist
 * that URL on their own entity (e.g. an expense's receipt). Open to any
 * authenticated user; the storage layer enforces size/type limits.
 *
 * <p>The {@code folder} is restricted to a fixed allowlist so a caller can't
 * inject an arbitrary Cloudinary path.
 */
@RestController
@RequestMapping("/uploads")
@RequiredArgsConstructor
public class UploadController {

    private static final Set<String> ALLOWED_FOLDERS = Set.of("receipts", "documents");
    private static final String DEFAULT_FOLDER = "receipts";

    private final FileStorageService fileStorageService;

    @PostMapping
    public ResponseEntity<UploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = DEFAULT_FOLDER) String folder) {

        String safeFolder = ALLOWED_FOLDERS.contains(folder) ? folder : DEFAULT_FOLDER;
        String url = fileStorageService.upload(file, safeFolder, null);
        return ResponseEntity.ok(new UploadResponse(url));
    }
}
