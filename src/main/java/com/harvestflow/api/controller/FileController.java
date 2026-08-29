package com.harvestflow.api.controller;

import com.harvestflow.api.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLConnection;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @GetMapping("/{subfolder}/{filename:.+}")
    public ResponseEntity<Resource> serve(@PathVariable String subfolder, @PathVariable String filename) {
        Resource resource = fileStorageService.load(subfolder + "/" + filename);
        String guessedType = URLConnection.guessContentTypeFromName(filename);
        MediaType contentType = guessedType != null
                ? MediaType.parseMediaType(guessedType)
                : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(resource);
    }
}
