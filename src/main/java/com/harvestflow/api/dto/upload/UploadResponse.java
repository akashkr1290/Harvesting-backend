package com.harvestflow.api.dto.upload;

import com.harvestflow.api.service.storage.FileStorageService;

public record UploadResponse(String fileKey, String url, String contentType, long sizeBytes) {
    public static UploadResponse from(FileStorageService.StoredFile stored) {
        return new UploadResponse(stored.key(), "/api/files/" + stored.key(), stored.contentType(), stored.sizeBytes());
    }
}
