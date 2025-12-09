package com.jreg.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AWS S3 implementation of the storage backend.
 */
@Component
public class BlobStorageBackend implements StorageBackend {
    
    private static final Logger logger = LoggerFactory.getLogger(BlobStorageBackend.class);
    
    private final String bucketName;
    private final S3Client s3Client;
    
    public BlobStorageBackend(S3Client s3Client, String bucketName) {
        this.bucketName = bucketName;
        this.s3Client = s3Client;
        createBucketIfNotExists();
    }
    
    private void createBucketIfNotExists() {
        try {
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.headBucket(headBucketRequest);
            logger.info("S3 bucket exists: {}", bucketName);
        } catch (NoSuchBucketException e) {
            try {
                logger.info("Creating S3 bucket: {}", bucketName);
                CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                        .bucket(bucketName)
                        .build();
                s3Client.createBucket(createBucketRequest);
            } catch (S3Exception createEx) {
                if (createEx.statusCode() == 409) {
                    // Bucket already exists and is owned by you
                    logger.info("S3 bucket already exists: {}", bucketName);
                } else {
                    logger.warn("Failed to create S3 bucket: {}. Error: {}", bucketName, createEx.getMessage());
                }
            }
        } catch (S3Exception e) {
            // Handle any other S3 exceptions gracefully (e.g., permission issues in test environments)
            logger.warn("S3 bucket check/creation skipped: {}. Error: {}", bucketName, e.getMessage());
        } catch (Exception e) {
            // Catch any other unexpected exceptions to prevent application startup failure
            logger.warn("Unexpected error during S3 bucket initialization: {}", e.getMessage());
        }
    }
    
    @Override
    public InputStream getObject(String key) {
        logger.debug("Getting S3 object: {}", key);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        return s3Client.getObject(getObjectRequest, ResponseTransformer.toInputStream());
    }
    
    @Override
    public InputStream getObjectRange(String key, String range) {
        logger.debug("Getting S3 object range: {} range={}", key, range);
        
        GetObjectRequest.Builder requestBuilder = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key);
        
        // Parse range header: "bytes=start-end"
        if (range != null && range.startsWith("bytes=")) {
            requestBuilder.range(range);
        }
        
        return s3Client.getObject(requestBuilder.build(), ResponseTransformer.toInputStream());
    }
    
    @Override
    public void putObject(String key, byte[] content, String contentType) {
        putObject(key, content, contentType, Map.of());
    }
    
    @Override
    public void putObject(String key, InputStream content, long contentLength, String contentType) {
        logger.debug("Putting S3 object: {} size={}", key, contentLength);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        
        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(content, contentLength));
    }
    
    @Override
    public void putObject(String key, byte[] content, String contentType, Map<String, String> metadata) {
        logger.debug("Putting S3 object: {} size={} metadata={}", key, content.length, metadata);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .metadata(metadata)
                .build();
        
        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(content));
    }
    
    @Override
    public boolean objectExists(String key) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.headObject(headObjectRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
    
    @Override
    public Map<String, String> getObjectMetadata(String key) {
        HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        HeadObjectResponse response = s3Client.headObject(headObjectRequest);
        return response.metadata();
    }
    
    @Override
    public long getObjectSize(String key) {
        HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        HeadObjectResponse response = s3Client.headObject(headObjectRequest);
        return response.contentLength();
    }
    
    @Override
    public void deleteObject(String key) {
        logger.debug("Deleting S3 object: {}", key);
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        s3Client.deleteObject(deleteObjectRequest);
    }
    
    @Override
    public List<String> listObjects(String prefix) {
        logger.debug("Listing S3 objects with prefix: {}", prefix);
        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();
        
        ListObjectsV2Response response = s3Client.listObjectsV2(listRequest);
        return response.contents().stream()
                .map(S3Object::key)
                .collect(Collectors.toList());
    }
    
    @Override
    public ListObjectsResult listObjects(String prefix, int maxKeys, String startAfter) {
        logger.debug("Listing S3 objects with prefix: {} maxKeys={} startAfter={}", prefix, maxKeys, startAfter);
        
        ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .maxKeys(maxKeys);
        
        if (startAfter != null && !startAfter.isEmpty()) {
            requestBuilder.startAfter(prefix + startAfter);
        }
        
        ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());
        
        List<String> keys = response.contents().stream()
                .map(S3Object::key)
                .map(name -> name.substring(prefix.length())) // Remove prefix
                .collect(Collectors.toList());
        
        // Check if there are more pages
        boolean isTruncated = response.isTruncated();
        String nextMarker = isTruncated && !keys.isEmpty() ? keys.get(keys.size() - 1) : null;
        
        return new ListObjectsResult(keys, nextMarker, isTruncated);
    }
}
