package com.jetski.shared.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Controller for local storage endpoints (development only).
 *
 * Implements the endpoints for simulated presigned URLs:
 * - PUT /v1/storage/local/upload/{key} - Upload a file
 * - GET /v1/storage/local/download/{key} - Download a file
 *
 * These endpoints are only active when storage.type=local.
 * In production, real S3 presigned URLs are used instead.
 */
@RestController
@RequestMapping("/v1/storage/local")
@ConditionalOnProperty(name = "storage.type", havingValue = "local")
@RequiredArgsConstructor
@Slf4j
public class LocalStorageController {

    private final LocalFileStorageService storageService;

    /**
     * Upload endpoint - simulates S3 presigned PUT URL.
     *
     * The key can be URL-encoded (with %2F for slashes) or use path segments.
     * Token validation is NOT implemented for simplicity in dev environment.
     *
     * @param encodedKey The storage key (may contain slashes for subdirectories)
     * @param token Upload token (not validated in local mode)
     * @param data File bytes
     * @return 200 OK on success
     */
    @PutMapping("/upload/**")
    public ResponseEntity<Void> uploadFile(
            jakarta.servlet.http.HttpServletRequest request,
            @RequestParam(value = "token", required = false) String token,
            @RequestBody byte[] data
    ) {
        // Extract key from the full path (everything after /upload/)
        String fullPath = request.getRequestURI();
        String encodedKey = fullPath.substring(fullPath.indexOf("/upload/") + "/upload/".length());
        try {
            // Decode the key (handle %2F -> /)
            String key = URLDecoder.decode(encodedKey, StandardCharsets.UTF_8);
            log.info("Local storage upload: key={}, size={} bytes, token={}", key, data.length, token);

            storageService.saveFile(key, data);

            log.info("File uploaded successfully: key={}", key);
            return ResponseEntity.ok().build();

        } catch (IOException e) {
            log.error("Failed to upload file: key={}", encodedKey, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Download endpoint - simulates S3 presigned GET URL.
     *
     * @param request HTTP request to extract key from path
     * @param token Download token (not validated in local mode)
     * @return File bytes with appropriate content type
     */
    @GetMapping("/download/**")
    public ResponseEntity<byte[]> downloadFile(
            jakarta.servlet.http.HttpServletRequest request,
            @RequestParam(value = "token", required = false) String token
    ) {
        // Extract key from the full path (everything after /download/)
        String fullPath = request.getRequestURI();
        String encodedKey = fullPath.substring(fullPath.indexOf("/download/") + "/download/".length());
        try {
            // Decode the key (handle %2F -> /)
            String key = URLDecoder.decode(encodedKey, StandardCharsets.UTF_8);
            log.info("Local storage download: key={}, token={}", key, token);

            byte[] data = storageService.readFile(key);

            // Determine content type from key extension
            MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
            if (key.endsWith(".jpg") || key.endsWith(".jpeg")) {
                contentType = MediaType.IMAGE_JPEG;
            } else if (key.endsWith(".png")) {
                contentType = MediaType.IMAGE_PNG;
            } else if (key.endsWith(".webp")) {
                contentType = MediaType.parseMediaType("image/webp");
            }

            log.info("File downloaded successfully: key={}, size={} bytes", key, data.length);
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .body(data);

        } catch (IOException e) {
            log.error("Failed to download file: key={}", encodedKey, e);
            return ResponseEntity.notFound().build();
        }
    }
}
