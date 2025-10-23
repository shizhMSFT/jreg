package com.jreg.controller;

import com.jreg.exception.BlobUploadInvalidException;
import com.jreg.exception.DigestInvalidException;
import com.jreg.model.Blob;
import com.jreg.model.ByteRange;
import com.jreg.model.Digest;
import com.jreg.model.UploadSession;
import com.jreg.service.BlobService;
import com.jreg.service.UploadSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.util.UUID;

/**
 * OCI Distribution Spec - Blob upload operations (chunked and monolithic)
 * Handles POST (initiate), PATCH (chunk), PUT (complete), DELETE (cancel), GET (status).
 */
@RestController
@RequestMapping("/v2")
public class UploadController {
    
    private static final Logger log = LoggerFactory.getLogger(UploadController.class);
    
    private final UploadSessionService uploadSessionService;
    private final BlobService blobService;

    public UploadController(UploadSessionService uploadSessionService, BlobService blobService) {
        this.uploadSessionService = uploadSessionService;
        this.blobService = blobService;
        log.info("UploadController initialized");
    }

    /**
     * Initiate a blob upload (OCI end-4a - POST)
     * POST /v2/{name}/blobs/uploads/
     * NOTE: {name:.+} pattern supports single-segment repo names (e.g., "myrepo")
     * Multi-segment names (e.g., "library/nginx") require custom path handling
     */
    @PostMapping("/{name:.+}/blobs/uploads/")
    public ResponseEntity<Void> initiateUpload(
            @PathVariable("name") String repository,
            @RequestParam(value = "digest", required = false) String digestStr,
            @RequestParam(value = "mount", required = false) String mountDigest,
            @RequestParam(value = "from", required = false) String fromRepository,
            HttpServletRequest request) {
        
        // Handle cross-repository blob mount
        if (mountDigest != null && fromRepository != null) {
            try {
                Digest digest = Digest.parse(mountDigest);
                boolean mounted = blobService.mountBlob(fromRepository, repository, digest);
                
                if (mounted) {
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .header(HttpHeaders.LOCATION, "/v2/" + repository + "/blobs/" + mountDigest)
                            .header("Docker-Content-Digest", mountDigest)
                            .build();
                }
                // Fall through to regular upload if mount fails
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        }
        
        // Handle monolithic upload (digest provided with POST)
        if (digestStr != null) {
            try {
                Digest expectedDigest = Digest.parse(digestStr);
                InputStream content = request.getInputStream();
                String contentType = request.getContentType();
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }
                
                Blob blob = blobService.storeBlobWithDigest(repository, content, expectedDigest, contentType);
                
                return ResponseEntity.status(HttpStatus.CREATED)
                        .header(HttpHeaders.LOCATION, "/v2/" + repository + "/blobs/" + blob.getDigest())
                        .header("Docker-Content-Digest", blob.getDigest().toString())
                        .build();
                        
            } catch (DigestInvalidException e) {
                // Let digest exceptions propagate directly
                throw e;
            } catch (Exception e) {
                throw new BlobUploadInvalidException("Failed to upload blob: " + e.getMessage());
            }
        }
        
        // Initiate chunked upload
        UploadSession session = uploadSessionService.startSession(repository);
        String uploadUrl = "/v2/" + repository + "/blobs/uploads/" + session.getSessionId();
        
        return ResponseEntity.accepted()
                .header(HttpHeaders.LOCATION, uploadUrl)
                .header("Docker-Upload-UUID", session.getSessionId().toString())
                .header("Range", "0-0")
                .build();
    }

    /**
     * Upload a chunk (OCI end-4b - PATCH)
     * PATCH /v2/{name}/blobs/uploads/{uuid}
     */
    @PatchMapping("/{name:.+}/blobs/uploads/{uuid}")
    public ResponseEntity<Void> uploadChunk(
            @PathVariable("name") String repository,
            @PathVariable("uuid") String uuidStr,
            @RequestHeader(value = "Content-Range", required = false) String contentRange,
            @RequestHeader(value = "Content-Length") long contentLength,
            HttpServletRequest request) {
        
        try {
            UUID sessionId = UUID.fromString(uuidStr);
            InputStream chunk = request.getInputStream();
            
            // Parse Content-Range header (e.g., "0-1023")
            long startByte = 0;
            long endByte = contentLength - 1;
            
            if (contentRange != null) {
                ByteRange range = ByteRange.parse(contentRange);
                startByte = range.start();
                endByte = range.end();
            }
            
            uploadSessionService.uploadChunk(sessionId, chunk, startByte, endByte);
            
            UploadSession session = uploadSessionService.getStatus(sessionId);
            String uploadUrl = "/v2/" + repository + "/blobs/uploads/" + sessionId;
            long lastByte = session.getLastUploadedByte();
            
            return ResponseEntity.accepted()
                    .header(HttpHeaders.LOCATION, uploadUrl)
                    .header("Docker-Upload-UUID", sessionId.toString())
                    .header("Range", "0-" + lastByte)
                    .build();
                    
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            throw new BlobUploadInvalidException("Failed to upload chunk: " + e.getMessage());
        }
    }

    /**
     * Complete the upload (OCI end-4c - PUT)
     * PUT /v2/{name}/blobs/uploads/{uuid}?digest={digest}
     */
    @PutMapping("/{name:.+}/blobs/uploads/{uuid}")
    public ResponseEntity<Void> completeUpload(
            @PathVariable("name") String repository,
            @PathVariable("uuid") String uuidStr,
            @RequestParam("digest") String digestStr,
            HttpServletRequest request) {
        
        try {
            UUID sessionId = UUID.fromString(uuidStr);
            Digest expectedDigest = Digest.parse(digestStr);
            
            // Check if there's a final chunk in the request body
            long contentLength = request.getContentLengthLong();
            if (contentLength > 0) {
                InputStream finalChunk = request.getInputStream();
                UploadSession session = uploadSessionService.getStatus(sessionId);
                long startByte = session.getLastUploadedByte() + 1;
                long endByte = startByte + contentLength - 1;
                uploadSessionService.uploadChunk(sessionId, finalChunk, startByte, endByte);
            }
            
            // Assemble chunks and store as blob
            InputStream assembledContent = uploadSessionService.completeSession(sessionId);
            String contentType = request.getContentType();
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
            
            Blob blob = blobService.storeBlobWithDigest(repository, assembledContent, expectedDigest, contentType);
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .header(HttpHeaders.LOCATION, "/v2/" + repository + "/blobs/" + blob.getDigest())
                    .header("Docker-Content-Digest", blob.getDigest().toString())
                    .build();
                    
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (DigestInvalidException e) {
            // Let digest exceptions propagate directly
            throw e;
        } catch (Exception e) {
            throw new BlobUploadInvalidException("Failed to complete upload: " + e.getMessage());
        }
    }

    /**
     * Get upload status (OCI end-4d - GET)
     * GET /v2/{name}/blobs/uploads/{uuid}
     */
    @GetMapping("/{name:.+}/blobs/uploads/{uuid}")
    public ResponseEntity<Void> getUploadStatus(
            @PathVariable("name") String repository,
            @PathVariable("uuid") String uuidStr) {
        
        try {
            UUID sessionId = UUID.fromString(uuidStr);
            UploadSession session = uploadSessionService.getStatus(sessionId);
            
            String uploadUrl = "/v2/" + repository + "/blobs/uploads/" + sessionId;
            long lastByte = session.getLastUploadedByte();
            
            return ResponseEntity.noContent()
                    .header(HttpHeaders.LOCATION, uploadUrl)
                    .header("Docker-Upload-UUID", sessionId.toString())
                    .header("Range", lastByte >= 0 ? "0-" + lastByte : "0-0")
                    .build();
                    
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Cancel an upload (OCI end-4e - DELETE)
     * DELETE /v2/{name}/blobs/uploads/{uuid}
     */
    @DeleteMapping("/{name:.+}/blobs/uploads/{uuid}")
    public ResponseEntity<Void> cancelUpload(
            @PathVariable("name") String repository,
            @PathVariable("uuid") String uuidStr) {
        
        try {
            UUID sessionId = UUID.fromString(uuidStr);
            uploadSessionService.cancelSession(sessionId);
            
            return ResponseEntity.noContent().build();
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
