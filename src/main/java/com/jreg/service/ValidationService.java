package com.jreg.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jreg.exception.ManifestInvalidException;
import com.jreg.exception.NameInvalidException;
import com.jreg.model.Digest;
import com.jreg.util.DigestCalculator;
import com.jreg.util.RegexValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Validates OCI specification compliance for names, digests, and manifests.
 */
@Service
public class ValidationService {
    private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);
    private final ObjectMapper objectMapper;

    public ValidationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Validate repository name follows OCI naming rules
     */
    public void validateRepositoryName(String repository) {
        if (!RegexValidator.isValidRepository(repository)) {
            throw new NameInvalidException("Invalid repository name: " + repository);
        }
    }

    /**
     * Validate tag name follows OCI naming rules
     */
    public void validateTagName(String tag) {
        if (!RegexValidator.isValidTag(tag)) {
            throw new IllegalArgumentException("Invalid tag name: " + tag);
        }
    }

    /**
     * Validate digest format and algorithm
     */
    public void validateDigest(String digestStr) {
        if (!RegexValidator.isValidDigest(digestStr)) {
            throw new IllegalArgumentException("Invalid digest format: " + digestStr);
        }
    }

    /**
     * Validate manifest JSON structure and content
     */
    public void validateManifest(byte[] manifestBytes, String providedDigest) {
        // Parse JSON
        JsonNode manifest;
        try {
            manifest = objectMapper.readTree(manifestBytes);
        } catch (IOException e) {
            throw new ManifestInvalidException("Invalid JSON in manifest", e);
        }

        // Verify required fields
        if (!manifest.has("schemaVersion")) {
            throw new ManifestInvalidException("Missing schemaVersion field");
        }

        if (!manifest.has("mediaType")) {
            throw new ManifestInvalidException("Missing mediaType field");
        }

        String mediaType = manifest.get("mediaType").asText();
        
        // Validate based on media type
        if (mediaType.equals("application/vnd.docker.distribution.manifest.v2+json") ||
            mediaType.equals("application/vnd.oci.image.manifest.v1+json")) {
            validateImageManifest(manifest);
        } else if (mediaType.equals("application/vnd.docker.distribution.manifest.list.v2+json") ||
                   mediaType.equals("application/vnd.oci.image.index.v1+json")) {
            validateManifestIndex(manifest);
        }

        // Verify digest matches content
        if (providedDigest != null) {
            Digest calculatedDigest = DigestCalculator.calculateSha256(manifestBytes);
            if (!calculatedDigest.toString().equals(providedDigest)) {
                throw new ManifestInvalidException(
                    "Digest mismatch: provided " + providedDigest + 
                    " but calculated " + calculatedDigest);
            }
        }
    }

    private void validateImageManifest(JsonNode manifest) {
        if (!manifest.has("config")) {
            throw new ManifestInvalidException("Image manifest missing config field");
        }
        
        if (!manifest.has("layers")) {
            throw new ManifestInvalidException("Image manifest missing layers field");
        }

        JsonNode config = manifest.get("config");
        validateDescriptor(config, "config");

        JsonNode layers = manifest.get("layers");
        if (!layers.isArray() || layers.isEmpty()) {
            throw new ManifestInvalidException("Layers must be a non-empty array");
        }

        for (int i = 0; i < layers.size(); i++) {
            validateDescriptor(layers.get(i), "layers[" + i + "]");
        }
    }

    private void validateManifestIndex(JsonNode index) {
        if (!index.has("manifests")) {
            throw new ManifestInvalidException("Manifest index missing manifests field");
        }

        JsonNode manifests = index.get("manifests");
        if (!manifests.isArray() || manifests.isEmpty()) {
            throw new ManifestInvalidException("Manifests must be a non-empty array");
        }

        for (int i = 0; i < manifests.size(); i++) {
            validateDescriptor(manifests.get(i), "manifests[" + i + "]");
        }
    }

    private void validateDescriptor(JsonNode descriptor, String fieldName) {
        if (!descriptor.has("mediaType")) {
            throw new ManifestInvalidException(fieldName + " missing mediaType");
        }

        if (!descriptor.has("digest")) {
            throw new ManifestInvalidException(fieldName + " missing digest");
        }

        if (!descriptor.has("size")) {
            throw new ManifestInvalidException(fieldName + " missing size");
        }

        String digest = descriptor.get("digest").asText();
        validateDigest(digest);

        long size = descriptor.get("size").asLong();
        if (size < 0) {
            throw new ManifestInvalidException(fieldName + " has negative size");
        }
    }
}
