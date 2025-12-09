package com.jreg.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jreg.exception.ManifestInvalidException;
import com.jreg.exception.ManifestNotFoundException;
import com.jreg.model.Digest;
import com.jreg.model.Manifest;
import com.jreg.storage.StorageBackend;
import com.jreg.util.DigestCalculator;
import com.jreg.util.S3KeyGenerator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages manifest storage and retrieval operations.
 */
@Service
public class ManifestService {
    private static final Logger logger = LoggerFactory.getLogger(ManifestService.class);
    
    private final StorageBackend storage;
    private final ValidationService validationService;
    private final ObjectMapper objectMapper;
    private final Counter manifestPushCounter;
    private final Counter manifestPullCounter;

    public ManifestService(StorageBackend storage,
                          ValidationService validationService,
                          ObjectMapper objectMapper,
                          MeterRegistry meterRegistry) {
        this.storage = storage;
        this.validationService = validationService;
        this.objectMapper = objectMapper;
        this.manifestPushCounter = Counter.builder("manifest_pushes_total")
                .description("Total number of manifest pushes")
                .register(meterRegistry);
        this.manifestPullCounter = Counter.builder("manifest_pulls_total")
                .description("Total number of manifest pulls")
                .register(meterRegistry);
    }

    /**
     * Store a manifest
     */
    public Manifest storeManifest(String repository, byte[] manifestBytes, String mediaType) {
        validationService.validateRepositoryName(repository);
        
        // Strip charset parameter from media type if present
        String cleanMediaType = mediaType;
        if (mediaType.contains(";")) {
            cleanMediaType = mediaType.substring(0, mediaType.indexOf(";")).trim();
        }
        
        // Calculate digest
        Digest digest = DigestCalculator.calculateSha256(manifestBytes);
        
        // Validate manifest structure
        validationService.validateManifest(manifestBytes, digest.toString());
        
        String key = S3KeyGenerator.manifestKey(repository, digest);
        
        // Store manifest
        storage.putObject(key, manifestBytes, cleanMediaType);
        
        Manifest manifest = new Manifest(digest, repository, cleanMediaType, manifestBytes);
        
        // Parse and extract subject if present (for referrers)
        try {
            JsonNode root = objectMapper.readTree(manifestBytes);
            if (root.has("subject")) {
                JsonNode subjectNode = root.get("subject");
                String subjectDigestStr = subjectNode.get("digest").asText();
                manifest.setSubject(Digest.parse(subjectDigestStr));
            }
            if (root.has("schemaVersion")) {
                manifest.setSchemaVersion(root.get("schemaVersion").asInt());
            }
        } catch (Exception e) {
            logger.warn("Failed to parse manifest metadata: {}", e.getMessage());
        }
        
        manifestPushCounter.increment();
        
        MDC.put("repository", repository);
        MDC.put("digest", digest.toString());
        logger.info("Stored manifest: {} bytes", manifestBytes.length);
        MDC.clear();
        
        return manifest;
    }

    /**
     * Get a manifest by digest
     */
    public Manifest getManifest(String repository, Digest digest) {
        validationService.validateRepositoryName(repository);
        
        String key = S3KeyGenerator.manifestKey(repository, digest);
        
        if (!storage.objectExists(key)) {
            throw new ManifestNotFoundException(repository, digest);
        }
        
        try (InputStream is = storage.getObject(key)) {
            byte[] content = is.readAllBytes();
            String contentType = storage.getObjectMetadata(key)
                    .getOrDefault("Content-Type", "application/vnd.oci.image.manifest.v1+json");
            
            Manifest manifest = new Manifest(digest, repository, contentType, content);
            
            manifestPullCounter.increment();
            
            MDC.put("repository", repository);
            MDC.put("digest", digest.toString());
            logger.info("Retrieved manifest: {} bytes", content.length);
            MDC.clear();
            
            return manifest;
        } catch (ManifestNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve manifest: " + e.getMessage(), e);
        }
    }

    /**
     * Get a manifest by tag (resolves tag to digest first)
     */
    public Manifest getManifestByTag(String repository, String tag, com.jreg.service.TagService tagService) {
        validationService.validateRepositoryName(repository);
        validationService.validateTagName(tag);
        
        Digest digest = tagService.resolveTag(repository, tag);
        return getManifest(repository, digest);
    }

    /**
     * Check if a manifest exists
     */
    public boolean manifestExists(String repository, Digest digest) {
        validationService.validateRepositoryName(repository);
        String key = S3KeyGenerator.manifestKey(repository, digest);
        return storage.objectExists(key);
    }

    /**
     * Delete a manifest
     */
    public void deleteManifest(String repository, Digest digest) {
        validationService.validateRepositoryName(repository);
        
        String key = S3KeyGenerator.manifestKey(repository, digest);
        
        if (!storage.objectExists(key)) {
            throw new ManifestNotFoundException(repository, digest);
        }
        
        // Check if this manifest has a subject (is a referrer)
        // If so, remove it from the subject's referrers index
        try (InputStream is = storage.getObject(key)) {
            byte[] manifestData = is.readAllBytes();
            JsonNode manifestJson = objectMapper.readTree(manifestData);
            
            if (manifestJson.has("subject")) {
                JsonNode subject = manifestJson.get("subject");
                String subjectDigestStr = subject.get("digest").asText();
                Digest subjectDigest = Digest.parse(subjectDigestStr);
                removeFromReferrersIndex(repository, subjectDigest, digest);
            }
        } catch (Exception e) {
            logger.warn("Failed to check/update referrers index during deletion: {}", e.getMessage());
        }
        
        storage.deleteObject(key);
        
        MDC.put("repository", repository);
        MDC.put("digest", digest.toString());
        logger.info("Deleted manifest");
        MDC.clear();
    }

    /**
     * Get referrers for a manifest (artifacts that reference this manifest)
     */
    public List<Manifest> getReferrers(String repository, Digest subjectDigest, String artifactType) {
        validationService.validateRepositoryName(repository);
        
        String referrersKey = S3KeyGenerator.referrersKey(repository, subjectDigest);
        List<Manifest> referrers = new ArrayList<>();
        
        if (!storage.objectExists(referrersKey)) {
            return referrers; // Empty list if no referrers
        }
        
        try (InputStream is = storage.getObject(referrersKey)) {
            byte[] referrersData = is.readAllBytes();
            JsonNode referrersIndex = objectMapper.readTree(referrersData);
            
            if (referrersIndex.has("manifests")) {
                JsonNode manifests = referrersIndex.get("manifests");
                for (JsonNode descriptorNode : manifests) {
                    String digestStr = descriptorNode.get("digest").asText();
                    
                    // Filter by artifact type if specified
                    if (artifactType != null) {
                        String descArtifactType = descriptorNode.has("artifactType") 
                                ? descriptorNode.get("artifactType").asText() 
                                : null;
                        if (!artifactType.equals(descArtifactType)) {
                            continue;
                        }
                    }
                    
                    Digest digest = Digest.parse(digestStr);
                    Manifest manifest = getManifest(repository, digest);
                    referrers.add(manifest);
                }
            }
            
            logger.debug("Retrieved {} referrers for {}", referrers.size(), subjectDigest);
            return referrers;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve referrers: " + e.getMessage(), e);
        }
    }

    /**
     * Get referrers index (returns the raw index with descriptors including artifactType)
     */
    public JsonNode getReferrersIndex(String repository, Digest subjectDigest, String artifactType) {
        validationService.validateRepositoryName(repository);
        
        String referrersKey = S3KeyGenerator.referrersKey(repository, subjectDigest);
        
        if (!storage.objectExists(referrersKey)) {
            // Return empty index
            var emptyIndex = objectMapper.createObjectNode();
            emptyIndex.put("schemaVersion", 2);
            emptyIndex.put("mediaType", "application/vnd.oci.image.index.v1+json");
            emptyIndex.putArray("manifests");
            return emptyIndex;
        }
        
        try (InputStream is = storage.getObject(referrersKey)) {
            byte[] referrersData = is.readAllBytes();
            JsonNode referrersIndex = objectMapper.readTree(referrersData);
            
            // Filter by artifactType if specified
            if (artifactType != null && referrersIndex.has("manifests")) {
                var filteredManifests = objectMapper.createArrayNode();
                JsonNode manifests = referrersIndex.get("manifests");
                for (JsonNode descriptor : manifests) {
                    String descArtifactType = descriptor.has("artifactType") 
                            ? descriptor.get("artifactType").asText() 
                            : null;
                    if (artifactType.equals(descArtifactType)) {
                        filteredManifests.add(descriptor);
                    }
                }
                ((com.fasterxml.jackson.databind.node.ObjectNode) referrersIndex).set("manifests", filteredManifests);
            }
            
            return referrersIndex;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve referrers index: " + e.getMessage(), e);
        }
    }

    /**
     * Update referrers index when a manifest with a subject is stored
     */
    public void updateReferrersIndex(String repository, Digest subjectDigest, Manifest referrer) {
        String referrersKey = S3KeyGenerator.referrersKey(repository, subjectDigest);
        
        try {
            JsonNode referrersIndex;
            
            // Load existing referrers index or create new one
            if (storage.objectExists(referrersKey)) {
                try (InputStream is = storage.getObject(referrersKey)) {
                    referrersIndex = objectMapper.readTree(is);
                }
            } else {
                var newIndex = objectMapper.createObjectNode();
                newIndex.put("schemaVersion", 2);
                newIndex.put("mediaType", "application/vnd.oci.image.index.v1+json");
                newIndex.putArray("manifests");
                referrersIndex = newIndex;
            }
            
            // Add new referrer descriptor (check for duplicates first)
            var manifestsArray = (com.fasterxml.jackson.databind.node.ArrayNode) referrersIndex.get("manifests");
            String referrerDigestStr = referrer.getDigest().toString();
            
            // Check if this referrer already exists in the index
            boolean exists = false;
            for (JsonNode existing : manifestsArray) {
                if (existing.get("digest").asText().equals(referrerDigestStr)) {
                    exists = true;
                    break;
                }
            }
            
            if (!exists) {
                var descriptor = objectMapper.createObjectNode()
                        .put("mediaType", referrer.getMediaType())
                        .put("digest", referrerDigestStr)
                        .put("size", referrer.getSize());
                
                // Add artifactType if present in the manifest
                try {
                    JsonNode manifestJson = objectMapper.readTree(referrer.getContent());
                    if (manifestJson.has("artifactType")) {
                        descriptor.put("artifactType", manifestJson.get("artifactType").asText());
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse manifest for artifactType: {}", e.getMessage());
                }
                
                manifestsArray.add(descriptor);
            }
            
            // Store updated index
            byte[] updatedIndex = objectMapper.writeValueAsBytes(referrersIndex);
            storage.putObject(
                    referrersKey,
                    new ByteArrayInputStream(updatedIndex),
                    updatedIndex.length,
                    "application/vnd.oci.image.index.v1+json"
            );
            
            logger.debug("Updated referrers index for {}", subjectDigest);
            
        } catch (Exception e) {
            logger.error("Failed to update referrers index: {}", e.getMessage(), e);
        }
    }

    /**
     * Remove a referrer from the referrers index when the referrer manifest is deleted
     */
    private void removeFromReferrersIndex(String repository, Digest subjectDigest, Digest referrerDigest) {
        String referrersKey = S3KeyGenerator.referrersKey(repository, subjectDigest);
        
        if (!storage.objectExists(referrersKey)) {
            return; // No referrers index to update
        }
        
        try {
            // Load existing referrers index
            JsonNode referrersIndex;
            try (InputStream is = storage.getObject(referrersKey)) {
                referrersIndex = objectMapper.readTree(is);
            }
            
            // Remove the referrer from the manifests array
            var manifestsArray = (com.fasterxml.jackson.databind.node.ArrayNode) referrersIndex.get("manifests");
            String referrerDigestStr = referrerDigest.toString();
            
            var newManifestsArray = objectMapper.createArrayNode();
            boolean removed = false;
            for (JsonNode descriptor : manifestsArray) {
                if (!descriptor.get("digest").asText().equals(referrerDigestStr)) {
                    newManifestsArray.add(descriptor);
                } else {
                    removed = true;
                }
            }
            
            if (removed) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) referrersIndex).set("manifests", newManifestsArray);
                
                // Store updated index
                byte[] updatedIndex = objectMapper.writeValueAsBytes(referrersIndex);
                storage.putObject(
                        referrersKey,
                        new ByteArrayInputStream(updatedIndex),
                        updatedIndex.length,
                        "application/vnd.oci.image.index.v1+json"
                );
                
                logger.debug("Removed referrer {} from referrers index for {}", referrerDigest, subjectDigest);
            }
            
        } catch (Exception e) {
            logger.error("Failed to remove from referrers index: {}", e.getMessage(), e);
        }
    }
}
