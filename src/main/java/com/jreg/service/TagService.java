package com.jreg.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jreg.exception.ManifestNotFoundException;
import com.jreg.model.Digest;
import com.jreg.model.Tag;
import com.jreg.storage.StorageBackend;
import com.jreg.util.S3KeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages tag-to-manifest mappings.
 */
@Service
public class TagService {
    private static final Logger logger = LoggerFactory.getLogger(TagService.class);
    
    private final StorageBackend storage;
    private final ValidationService validationService;
    private final ObjectMapper objectMapper;

    public TagService(StorageBackend storage,
                     ValidationService validationService,
                     ObjectMapper objectMapper) {
        this.storage = storage;
        this.validationService = validationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create or update a tag pointing to a manifest
     */
    public Tag tagManifest(String repository, String tagName, Digest manifestDigest) {
        validationService.validateRepositoryName(repository);
        validationService.validateTagName(tagName);
        
        String key = S3KeyGenerator.tagKey(repository, tagName);
        
        // Create tag metadata
        Map<String, String> tagData = new HashMap<>();
        tagData.put("digest", manifestDigest.toString());
        tagData.put("repository", repository);
        tagData.put("tag", tagName);
        
        try {
            byte[] tagBytes = objectMapper.writeValueAsBytes(tagData);
            storage.putObject(
                    key,
                    new ByteArrayInputStream(tagBytes),
                    tagBytes.length,
                    "application/json"
            );
            
            Tag tag = new Tag(repository, tagName, manifestDigest);
            
            MDC.put("repository", repository);
            MDC.put("tag", tagName);
            MDC.put("digest", manifestDigest.toString());
            logger.info("Tagged manifest");
            MDC.clear();
            
            return tag;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to tag manifest: " + e.getMessage(), e);
        }
    }

    /**
     * Resolve a tag to its manifest digest
     */
    public Digest resolveTag(String repository, String tagName) {
        validationService.validateRepositoryName(repository);
        validationService.validateTagName(tagName);
        
        String key = S3KeyGenerator.tagKey(repository, tagName);
        
        if (!storage.objectExists(key)) {
            throw new ManifestNotFoundException(repository, tagName);
        }
        
        try (InputStream is = storage.getObject(key)) {
            byte[] tagBytes = is.readAllBytes();
            @SuppressWarnings("unchecked")
            Map<String, String> tagData = objectMapper.readValue(tagBytes, Map.class);
            String digestStr = tagData.get("digest");
            
            return Digest.parse(digestStr);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve tag: " + e.getMessage(), e);
        }
    }

    /**
     * List all tags in a repository
     */
    public List<String> listTags(String repository) {
        validationService.validateRepositoryName(repository);
        
        String prefix = "tags/" + repository + "/";
        List<String> tagNames = new ArrayList<>();
        
        List<String> keys = storage.listObjects(prefix);
        for (String key : keys) {
            // Extract tag name from key: tags/{repository}/{tagName}
            String tagName = key.substring(prefix.length());
            tagNames.add(tagName);
        }
        
        logger.debug("Listed {} tags for repository {}", tagNames.size(), repository);
        return tagNames;
    }

    /**
     * Delete a tag
     */
    public void deleteTag(String repository, String tagName) {
        validationService.validateRepositoryName(repository);
        validationService.validateTagName(tagName);
        
        String key = S3KeyGenerator.tagKey(repository, tagName);
        
        if (!storage.objectExists(key)) {
            throw new ManifestNotFoundException(repository, tagName);
        }
        
        storage.deleteObject(key);
        
        MDC.put("repository", repository);
        MDC.put("tag", tagName);
        logger.info("Deleted tag");
        MDC.clear();
    }

    /**
     * Check if a tag exists
     */
    public boolean tagExists(String repository, String tagName) {
        validationService.validateRepositoryName(repository);
        validationService.validateTagName(tagName);
        
        String key = S3KeyGenerator.tagKey(repository, tagName);
        return storage.objectExists(key);
    }
}
