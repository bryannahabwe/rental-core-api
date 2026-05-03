package com.cognix.rentalcoreapi.shared.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    /**
     * Uploads a file and returns the public URL.
     *
     * @param file     the file to upload
     * @param folder   the Cloudinary folder to organise uploads e.g. "logos", "documents"
     * @param publicId optional custom public ID — pass null to let Cloudinary generate one
     */
    String upload(MultipartFile file, String folder, String publicId);

    /**
     * Deletes a file by its public ID.
     */
    void delete(String publicId);
}