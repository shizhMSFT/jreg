package com.jreg.controller;

import com.jreg.exception.BlobNotFoundException;
import com.jreg.exception.DigestInvalidException;
import com.jreg.model.Blob;
import com.jreg.model.Digest;
import com.jreg.service.BlobService;
import com.jreg.storage.StorageBackend;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;

/**
 * OCI Distribution Spec - Blob operations
 * Handles blob HEAD (check existence) and GET (download) operations.
 */
@RestController
@RequestMapping("/v2")
public class BlobController {
    
    private final BlobService blobService;
    private final StorageBackend storage;

    public BlobController(BlobService blobService, StorageBackend storage) {
        this.blobService = blobService;
        this.storage = storage;
    }

    /**
     * Check if a blob exists (OCI end-3)
     * HEAD /v2/{name}/blobs/{digest}
     */
    @RequestMapping(
            method = RequestMethod.HEAD,
            path = "/{name:.+}/blobs/{digest}"
    )
    public void checkBlobExists(
            @PathVariable("name") String repository,
            @PathVariable("digest") String digestStr,
            HttpServletResponse response) throws IOException {
        
        try {
            Digest digest = Digest.parse(digestStr);
            
            if (!blobService.blobExists(repository, digest)) {
                throw new BlobNotFoundException(repository, digest);
            }
            
            Blob blob = blobService.getBlobMetadata(repository, digest);
            
            response.setStatus(HttpStatus.OK.value());
            response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(blob.getSize()));
            response.setHeader(HttpHeaders.CONTENT_TYPE, blob.getMediaType());
            response.setHeader("Docker-Content-Digest", digest.toString());
                    
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
        }
    }

    /**
     * Download a blob (OCI end-4)
     * GET /v2/{name}/blobs/{digest}
     * Supports HTTP Range requests for partial content delivery
     */
    @GetMapping("/{name:.+}/blobs/{digest}")
    public ResponseEntity<InputStreamResource> downloadBlob(
            @PathVariable("name") String repository,
            @PathVariable("digest") String digestStr,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {
        
        try {
            Digest digest = Digest.parse(digestStr);
            
            Blob blob = blobService.getBlobMetadata(repository, digest);
            
            // Handle Range requests (RFC 7233)
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                return handleRangeRequest(repository, digest, blob, rangeHeader);
            }
            
            // Full content download
            InputStream content = blobService.getBlobContent(repository, digest);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(blob.getSize()))
                    .header(HttpHeaders.CONTENT_TYPE, blob.getMediaType())
                    .header("Docker-Content-Digest", digest.toString())
                    .body(new InputStreamResource(content));
                    
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Handle HTTP Range request for partial content delivery
     * Supports:
     * - bytes=start-end (specific range)
     * - bytes=-suffix (last N bytes)
     * - bytes=start- (from start to end)
     */
    private ResponseEntity<InputStreamResource> handleRangeRequest(
            String repository, Digest digest, Blob blob, String rangeHeader) {
        
        String rangeValue = rangeHeader.substring(6); // Remove "bytes="
        long totalSize = blob.getSize();
        long rangeStart;
        long rangeEnd;
        
        if (rangeValue.startsWith("-")) {
            // Suffix-byte-range: bytes=-10 (last 10 bytes)
            long suffix = Long.parseLong(rangeValue.substring(1));
            rangeStart = Math.max(0, totalSize - suffix);
            rangeEnd = totalSize - 1;
        } else if (rangeValue.endsWith("-")) {
            // From start to end: bytes=100-
            rangeStart = Long.parseLong(rangeValue.substring(0, rangeValue.length() - 1));
            rangeEnd = totalSize - 1;
        } else {
            // Specific range: bytes=0-9
            String[] parts = rangeValue.split("-");
            rangeStart = Long.parseLong(parts[0]);
            rangeEnd = Long.parseLong(parts[1]);
        }
        
        // Validate range
        if (rangeStart < 0 || rangeEnd >= totalSize || rangeStart > rangeEnd) {
            return ResponseEntity.status(416) // Range Not Satisfiable
                    .header("Content-Range", "bytes */" + totalSize)
                    .build();
        }
        
        long contentLength = rangeEnd - rangeStart + 1;
        String s3Range = "bytes=" + rangeStart + "-" + rangeEnd;
        
        try {
            String key = "blobs/" + digest.algorithm() + "/" + digest.hex().substring(0, 2) + "/" + digest.hex();
            InputStream content = storage.getObjectRange(key, s3Range);
            
            return ResponseEntity.status(206) // Partial Content
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                    .header(HttpHeaders.CONTENT_TYPE, blob.getMediaType())
                    .header("Content-Range", "bytes " + rangeStart + "-" + rangeEnd + "/" + totalSize)
                    .header("Docker-Content-Digest", digest.toString())
                    .body(new InputStreamResource(content));
                    
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Delete a blob (OCI end-9)
     * DELETE /v2/{name}/blobs/{digest}
     * 
     * Returns 202 Accepted whether the blob exists or not (idempotent).
     * Returns 400 Bad Request with JSON error if the digest format is invalid.
     */
    @DeleteMapping("/{name:.+}/blobs/{digest}")
    public ResponseEntity<Void> deleteBlob(
            @PathVariable("name") String repository,
            @PathVariable("digest") String digestStr) {
        
        try {
            Digest digest = Digest.parse(digestStr);
            
            // Try to delete the blob, but don't fail if it doesn't exist
            // OCI spec requires idempotent DELETE - always return 202
            try {
                blobService.deleteBlob(repository, digest);
            } catch (BlobNotFoundException e) {
                // Blob doesn't exist - still return 202 (idempotent)
            }
            
            return ResponseEntity.accepted().build();
            
        } catch (IllegalArgumentException e) {
            // Invalid digest format - throw proper exception for JSON error response
            throw new DigestInvalidException(digestStr);
        }
    }

    /**
     * Check if blob exists in source repository for mounting (OCI end-11)
     * This is used during blob upload with mount parameter
     */
    @RequestMapping(
            method = RequestMethod.HEAD,
            path = "/{name:.+}/blobs/{digest}",
            params = "mount"
    )
    public void checkBlobForMount(
            @PathVariable("name") String repository,
            @PathVariable("digest") String digestStr,
            @RequestParam("mount") String mountDigest,
            @RequestParam("from") String fromRepository,
            HttpServletResponse response) throws IOException {
        
        try {
            Digest mountDigestParsed = Digest.parse(mountDigest);
            
            // Check if blob exists in source repository
            if (!blobService.blobExists(fromRepository, mountDigestParsed)) {
                response.setStatus(HttpStatus.NOT_FOUND.value());
                return;
            }
            
            // Mount blob to target repository
            boolean mounted = blobService.mountBlob(fromRepository, repository, mountDigestParsed);
            
            if (mounted) {
                response.setStatus(HttpStatus.CREATED.value());
                response.setHeader(HttpHeaders.LOCATION, "/v2/" + repository + "/blobs/" + mountDigest);
                response.setHeader("Docker-Content-Digest", mountDigest);
            } else {
                response.setStatus(HttpStatus.NOT_FOUND.value());
            }
            
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
        }
    }
}
