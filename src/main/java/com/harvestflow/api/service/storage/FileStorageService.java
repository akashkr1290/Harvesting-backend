package com.harvestflow.api.service.storage;

import com.harvestflow.api.exception.InvalidUploadException;
import com.harvestflow.api.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * Local-disk file storage. Every uploaded file (plot photos, weight slips)
 * goes through here rather than screens holding raw bytes or, worse, just
 * a device-local filename that means nothing off that device.
 *
 * This is intentionally the only place in the codebase that knows *how*
 * files are stored — callers only ever see a {@link StoredFile} key like
 * "weight-slips/&lt;uuid&gt;.jpg". Swapping to S3-compatible object storage
 * later (per the original Working Approach doc) means rewriting this one
 * class; no controller, entity, or DTO needs to change.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final Path rootDir;

    public FileStorageService(@Value("${harvestflow.uploads.dir:uploads}") String uploadsDir) {
        this.rootDir = Paths.get(uploadsDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not initialize upload storage directory: " + rootDir, e);
        }
        log.info("File storage initialized at {}", rootDir);
    }

    public StoredFile store(MultipartFile file, String subfolder, Set<String> allowedContentTypes, long maxSizeBytes) {
        if (file.isEmpty()) {
            throw new InvalidUploadException("Uploaded file is empty.");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new InvalidUploadException(
                    "File exceeds the maximum allowed size of " + (maxSizeBytes / (1024 * 1024)) + " MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !allowedContentTypes.contains(contentType)) {
            throw new InvalidUploadException(
                    "Unsupported file type" + (contentType != null ? ": " + contentType : "") +
                            ". Allowed: " + String.join(", ", allowedContentTypes));
        }

        String storedName = UUID.randomUUID() + extensionFor(contentType);
        Path subPath = rootDir.resolve(subfolder).normalize();
        if (!subPath.startsWith(rootDir)) {
            throw new InvalidUploadException("Invalid storage location.");
        }

        try {
            Files.createDirectories(subPath);
            Path target = subPath.resolve(storedName);
            file.transferTo(target);
            String key = subfolder + "/" + storedName;
            log.info("Stored upload '{}' ({} bytes, {}) as {}",
                    file.getOriginalFilename(), file.getSize(), contentType, key);
            return new StoredFile(key, contentType, file.getSize(), file.getOriginalFilename());
        } catch (IOException e) {
            log.error("Failed to store uploaded file '{}'", file.getOriginalFilename(), e);
            throw new UncheckedIOException("Failed to store uploaded file.", e);
        }
    }

    public Resource load(String key) {
        // Defense in depth: the resolve().normalize().startsWith() check
        // below is the actual, sufficient guard against path traversal —
        // this upfront rejection just makes that intent unambiguous to
        // read, rather than relying on reasoning through Path semantics.
        if (key.contains("..")) {
            throw new InvalidUploadException("Invalid file reference.");
        }

        Path file = rootDir.resolve(key).normalize();
        if (!file.startsWith(rootDir)) {
            throw new InvalidUploadException("Invalid file reference.");
        }
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("File not found: " + key);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("File not found: " + key);
        }
    }

    /**
     * Resolves a stored file's key to an actual filesystem path, for callers
     * that need real file access (image decoding, hashing) rather than a
     * {@link Resource}/{@link org.springframework.core.io.InputStreamResource}.
     * Added for Phase 4 image verification — same path-traversal guard as
     * {@link #load(String)}, just returning {@link Path} instead of {@link Resource}.
     */
    public Path resolvedPath(String key) {
        if (key.contains("..")) {
            throw new InvalidUploadException("Invalid file reference.");
        }
        Path file = rootDir.resolve(key).normalize();
        if (!file.startsWith(rootDir)) {
            throw new InvalidUploadException("Invalid file reference.");
        }
        return file;
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }

    public record StoredFile(String key, String contentType, long sizeBytes, String originalFilename) {
    }
}
