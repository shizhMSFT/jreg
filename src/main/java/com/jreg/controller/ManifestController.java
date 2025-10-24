package com.jreg.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.jreg.exception.DigestInvalidException;
import com.jreg.exception.ManifestInvalidException;
import com.jreg.model.Digest;
import com.jreg.model.Manifest;
import com.jreg.service.ManifestService;
import com.jreg.service.TagService;
import com.jreg.util.RegexValidator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * OCI Distribution Spec - Manifest operations
 * Handles manifest PUT (push), GET (pull), HEAD (check), and DELETE.
 */
@RestController
@RequestMapping("/v2")
public class ManifestController {
    
    private final ManifestService manifestService;
    private final TagService tagService;

    public ManifestController(ManifestService manifestService, TagService tagService) {
        this.manifestService = manifestService;
        this.tagService = tagService;
    }

    /**
     * Push a manifest (OCI end-7)
     * PUT /v2/{name}/manifests/{reference}
     * Reference can be a tag or digest
     */
    @PutMapping("/{name:.+}/manifests/{reference}")
    public ResponseEntity<Void> pushManifest(
            @PathVariable("name") String repository,
            @PathVariable String reference,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE) String contentType,
            HttpServletRequest request) {
        
        try {
            byte[] manifestBytes = request.getInputStream().readAllBytes();
            
            // Store manifest
            Manifest manifest = manifestService.storeManifest(repository, manifestBytes, contentType);
            
            // If reference is a tag, create/update the tag
            if (!RegexValidator.isValidDigest(reference)) {
                tagService.tagManifest(repository, reference, manifest.getDigest());
            }
            
            // If manifest has a subject, update referrers index
            if (manifest.getSubject() != null) {
                manifestService.updateReferrersIndex(repository, manifest.getSubject(), manifest);
            }
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .header(HttpHeaders.LOCATION, "/v2/" + repository + "/manifests/" + manifest.getDigest())
                    .header("Docker-Content-Digest", manifest.getDigest().toString())
                    .build();
                    
        } catch (ManifestInvalidException e) {
            // Let manifest validation exceptions propagate directly
            throw e;
        } catch (DigestInvalidException e) {
            // Let digest exceptions propagate directly  
            throw e;
        } catch (Exception e) {
            throw new ManifestInvalidException("Failed to store manifest: " + e.getMessage());
        }
    }

    /**
     * Pull a manifest (OCI end-3)
     * GET /v2/{name}/manifests/{reference}
     */
    @GetMapping("/{name:.+}/manifests/{reference}")
    public ResponseEntity<byte[]> pullManifest(
            @PathVariable("name") String repository,
            @PathVariable String reference) {
        
        try {
            Manifest manifest;
            
            // Check if reference is a digest or tag
            if (RegexValidator.isValidDigest(reference)) {
                Digest digest = Digest.parse(reference);
                manifest = manifestService.getManifest(repository, digest);
            } else {
                manifest = manifestService.getManifestByTag(repository, reference, tagService);
            }
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, manifest.getMediaType())
                    .header("Docker-Content-Digest", manifest.getDigest().toString())
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(manifest.getSize()))
                    .body(manifest.getContent());
                    
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Check if a manifest exists (OCI end-2)
     * HEAD /v2/{name}/manifests/{reference}
     */
    @RequestMapping(
            method = RequestMethod.HEAD,
            path = "/{name:.+}/manifests/{reference}"
    )
    public ResponseEntity<Void> checkManifestExists(
            @PathVariable("name") String repository,
            @PathVariable String reference) {
        
        try {
            Manifest manifest;
            
            // Check if reference is a digest or tag
            if (RegexValidator.isValidDigest(reference)) {
                Digest digest = Digest.parse(reference);
                manifest = manifestService.getManifest(repository, digest);
            } else {
                manifest = manifestService.getManifestByTag(repository, reference, tagService);
            }
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, manifest.getMediaType())
                    .header("Docker-Content-Digest", manifest.getDigest().toString())
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(manifest.getSize()))
                    .build();
                    
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete a manifest (OCI end-9)
     * DELETE /v2/{name}/manifests/{reference}
     * 
     * Returns 202 Accepted whether the manifest exists or not (idempotent).
     * Returns 400 Bad Request if the reference format is invalid.
     * 
     * DELETE by tag: Deletes ONLY the tag, manifest remains accessible by digest
     * DELETE by digest: Deletes the actual manifest content
     */
    @DeleteMapping("/{name:.+}/manifests/{reference}")
    public ResponseEntity<Void> deleteManifest(
            @PathVariable("name") String repository,
            @PathVariable String reference) {
        
        // Validate reference format
        if (!RegexValidator.isValidDigest(reference) && !RegexValidator.isValidTag(reference)) {
            // Invalid reference format - return 400 (not 202, as this is a client error)
            return ResponseEntity.badRequest().build();
        }
        
        try {
            // Check if reference is a tag or digest
            if (RegexValidator.isValidDigest(reference)) {
                // DELETE by digest - delete the actual manifest
                Digest digest = Digest.parse(reference);
                try {
                    manifestService.deleteManifest(repository, digest);
                } catch (Exception e) {
                    // Manifest doesn't exist - still return 202 (idempotent)
                }
            } else {
                // DELETE by tag - only delete the tag reference, NOT the manifest
                try {
                    tagService.deleteTag(repository, reference);
                } catch (Exception e) {
                    // Tag doesn't exist - still return 202 (idempotent)
                }
            }
            
            return ResponseEntity.accepted().build();
            
        } catch (IllegalArgumentException e) {
            // Invalid digest format - return 400
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * List tags (OCI end-8a)
     * GET /v2/{name}/tags/list
     */
    @GetMapping("/{name:.+}/tags/list")
    public ResponseEntity<Map<String, Object>> listTags(
            @PathVariable("name") String repository,
            @RequestParam(value = "n", required = false) Integer limit,
            @RequestParam(required = false) String last) {
        
        List<String> tags = tagService.listTags(repository);
        
        // Apply pagination if requested
        if (last != null) {
            int lastIndex = tags.indexOf(last);
            if (lastIndex >= 0) {
                tags = tags.subList(lastIndex + 1, tags.size());
            }
        }
        
        boolean hasMore = false;
        String lastTag = null;
        
        if (limit != null && limit > 0 && tags.size() > limit) {
            hasMore = true;
            lastTag = tags.get(limit - 1);
            tags = tags.subList(0, limit);
        }
        
        Map<String, Object> response = Map.of(
                "name", repository,
                "tags", tags
        );
        
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        
        // Add Link header if there are more results
        if (hasMore && lastTag != null) {
            String linkHeader = "</v2/%s/tags/list?n=%d&last=%s>; rel=\"next\"".formatted(
                    repository, limit, lastTag);
            builder.header("Link", linkHeader);
        }
        
        return builder.body(response);
    }

    /**
     * List referrers (OCI Referrers API)
     * GET /v2/{name}/referrers/{digest}
     */
    @GetMapping("/{name:.+}/referrers/{digest}")
    public ResponseEntity<String> listReferrers(
            @PathVariable("name") String repository,
            @PathVariable("digest") String digestStr,
            @RequestParam(required = false) String artifactType) {
        
        try {
            Digest subjectDigest = Digest.parse(digestStr);
            
            JsonNode referrersIndex = manifestService.getReferrersIndex(repository, subjectDigest, artifactType);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/vnd.oci.image.index.v1+json")
                    .body(referrersIndex.toString());
                    
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
