package com.jreg.service;

import com.jreg.exception.BlobNotFoundException;
import com.jreg.exception.DigestInvalidException;
import com.jreg.model.Blob;
import com.jreg.model.Digest;
import com.jreg.storage.StorageBackend;
import com.jreg.util.DigestCalculator;
import com.jreg.util.S3KeyGenerator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * Manages blob storage operations with content-addressable storage.
 */
@Service
public class BlobService {
    private static final Logger logger = LoggerFactory.getLogger(BlobService.class);
    
    private final StorageBackend storage;
    private final ValidationService validationService;
    private final Counter blobUploadCounter;
    private final Counter blobDownloadCounter;

    public BlobService(StorageBackend storage, 
                      ValidationService validationService,
                      MeterRegistry meterRegistry) {
        this.storage = storage;
        this.validationService = validationService;
        this.blobUploadCounter = Counter.builder("blob_uploads_total")
                .description("Total number of blob uploads")
                .register(meterRegistry);
        this.blobDownloadCounter = Counter.builder("blob_downloads_total")
                .description("Total number of blob downloads")
                .register(meterRegistry);
    }

    /**
     * Check if a blob exists in storage
     */
    public boolean blobExists(String repository, Digest digest) {
        validationService.validateRepositoryName(repository);
        String key = S3KeyGenerator.blobKey(digest);
        return storage.objectExists(key);
    }

    /**
     * Get blob metadata without content
     */
    public Blob getBlobMetadata(String repository, Digest digest) {
        validationService.validateRepositoryName(repository);
        String key = S3KeyGenerator.blobKey(digest);
        
        if (!storage.objectExists(key)) {
            throw new BlobNotFoundException(repository, digest);
        }

        long size = storage.getObjectSize(key);
        String contentType = storage.getObjectMetadata(key).getOrDefault("Content-Type", "application/octet-stream");

        MDC.put("repository", repository);
        MDC.put("digest", digest.toString());
        logger.debug("Retrieved blob metadata: {} bytes", size);
        MDC.clear();

        return new Blob(digest, size, contentType, key);
    }

    /**
     * Get blob content stream
     */
    public InputStream getBlobContent(String repository, Digest digest) {
        validationService.validateRepositoryName(repository);
        String key = S3KeyGenerator.blobKey(digest);
        
        if (!storage.objectExists(key)) {
            throw new BlobNotFoundException(repository, digest);
        }

        blobDownloadCounter.increment();
        
        MDC.put("repository", repository);
        MDC.put("digest", digest.toString());
        logger.info("Downloading blob");
        MDC.clear();

        return storage.getObject(key);
    }

    /**
     * Store blob with automatic digest verification
     */
    public Blob storeBlob(String repository, InputStream content, String contentType) {
        validationService.validateRepositoryName(repository);
        
        // Calculate digest while storing
        Digest digest = DigestCalculator.calculateSha256(content);
        String key = S3KeyGenerator.blobKey(digest);

        // Check if already exists (deduplication)
        if (storage.objectExists(key)) {
            long size = storage.getObjectSize(key);
            logger.info("Blob {} already exists (deduplication), skipping upload", digest);
            return new Blob(digest, size, contentType, key);
        }

        // Store to S3
        byte[] contentBytes;
        try {
            contentBytes = content.readAllBytes();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read blob content: " + e.getMessage(), e);
        }
        storage.putObject(key, new java.io.ByteArrayInputStream(contentBytes), contentBytes.length, contentType);
        long size = storage.getObjectSize(key);

        blobUploadCounter.increment();

        MDC.put("repository", repository);
        MDC.put("digest", digest.toString());
        logger.info("Stored blob: {} bytes", size);
        MDC.clear();

        return new Blob(digest, size, contentType, key);
    }

    /**
     * Store blob with explicit digest verification
     */
    public Blob storeBlobWithDigest(String repository, InputStream content, 
                                   Digest expectedDigest, String contentType) {
        validationService.validateRepositoryName(repository);
        
        String key = S3KeyGenerator.blobKey(expectedDigest);

        // Check if already exists (deduplication)
        if (storage.objectExists(key)) {
            long size = storage.getObjectSize(key);
            logger.info("Blob {} already exists (deduplication), skipping upload", expectedDigest);
            return new Blob(expectedDigest, size, contentType, key);
        }

        // Store to S3
        byte[] contentBytes;
        try {
            contentBytes = content.readAllBytes();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read blob content: " + e.getMessage(), e);
        }
        storage.putObject(key, new java.io.ByteArrayInputStream(contentBytes), contentBytes.length, contentType);
        
        // Verify digest after upload
        try (InputStream is = storage.getObject(key)) {
            Digest actualDigest = DigestCalculator.calculateSha256(is);
            if (!actualDigest.equals(expectedDigest)) {
                storage.deleteObject(key);
                throw new DigestInvalidException(
                    "Digest mismatch: expected " + expectedDigest + " but got " + actualDigest);
            }
        } catch (Exception e) {
            storage.deleteObject(key);
            throw new DigestInvalidException("Failed to verify digest: " + e.getMessage());
        }

        long size = storage.getObjectSize(key);
        blobUploadCounter.increment();

        MDC.put("repository", repository);
        MDC.put("digest", expectedDigest.toString());
        logger.info("Stored blob with digest verification: {} bytes", size);
        MDC.clear();

        return new Blob(expectedDigest, size, contentType, key);
    }

    /**
     * Mount blob from source repository to target (cross-repository blob mount)
     */
    public boolean mountBlob(String sourceRepo, String targetRepo, Digest digest) {
        validationService.validateRepositoryName(sourceRepo);
        validationService.validateRepositoryName(targetRepo);

        String key = S3KeyGenerator.blobKey(digest);
        
        if (!storage.objectExists(key)) {
            return false;
        }

        // Blobs are content-addressable and shared across repositories
        // No actual copy needed, just verify existence
        logger.info("Mounted blob {} from {} to {}", digest, sourceRepo, targetRepo);
        return true;
    }

    /**
     * Delete a blob
     */
    public void deleteBlob(String repository, Digest digest) {
        validationService.validateRepositoryName(repository);
        String key = S3KeyGenerator.blobKey(digest);
        
        if (!storage.objectExists(key)) {
            throw new BlobNotFoundException(repository, digest);
        }

        storage.deleteObject(key);

        MDC.put("repository", repository);
        MDC.put("digest", digest.toString());
        logger.info("Deleted blob");
        MDC.clear();
    }
}
