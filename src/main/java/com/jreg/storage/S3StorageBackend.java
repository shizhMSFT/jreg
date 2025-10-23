package com.jreg.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * S3 implementation of the storage backend.
 */
@Component
public class S3StorageBackend implements StorageBackend {
    
    private static final Logger logger = LoggerFactory.getLogger(S3StorageBackend.class);
    
    private final S3Client s3Client;
    private final String bucketName;
    
    public S3StorageBackend(S3Client s3Client, String s3BucketName) {
        this.s3Client = s3Client;
        this.bucketName = s3BucketName;
        ensureBucketExists();
    }
    
    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            logger.info("S3 bucket exists: {}", bucketName);
        } catch (NoSuchBucketException e) {
            logger.info("Creating S3 bucket: {}", bucketName);
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        }
    }
    
    @Override
    public InputStream getObject(String key) {
        logger.debug("Getting object: {}", key);
        return s3Client.getObject(
                GetObjectRequest.builder().bucket(bucketName).key(key).build(),
                ResponseTransformer.toInputStream()
        );
    }
    
    @Override
    public InputStream getObjectRange(String key, String range) {
        logger.debug("Getting object range: {} range={}", key, range);
        return s3Client.getObject(
                GetObjectRequest.builder().bucket(bucketName).key(key).range(range).build(),
                ResponseTransformer.toInputStream()
        );
    }
    
    @Override
    public void putObject(String key, byte[] content, String contentType) {
        putObject(key, content, contentType, Map.of());
    }
    
    @Override
    public void putObject(String key, InputStream content, long contentLength, String contentType) {
        logger.debug("Putting object: {} size={}", key, contentLength);
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(contentType)
                        .contentLength(contentLength)
                        .build(),
                RequestBody.fromInputStream(content, contentLength)
        );
    }
    
    @Override
    public void putObject(String key, byte[] content, String contentType, Map<String, String> metadata) {
        logger.debug("Putting object: {} size={} metadata={}", key, content.length, metadata);
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(contentType)
                        .contentLength((long) content.length)
                        .metadata(metadata)
                        .build(),
                RequestBody.fromBytes(content)
        );
    }
    
    @Override
    public boolean objectExists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
    
    @Override
    public Map<String, String> getObjectMetadata(String key) {
        HeadObjectResponse response = s3Client.headObject(
                HeadObjectRequest.builder().bucket(bucketName).key(key).build()
        );
        return response.metadata();
    }
    
    @Override
    public long getObjectSize(String key) {
        HeadObjectResponse response = s3Client.headObject(
                HeadObjectRequest.builder().bucket(bucketName).key(key).build()
        );
        return response.contentLength();
    }
    
    @Override
    public void deleteObject(String key) {
        logger.debug("Deleting object: {}", key);
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
    }
    
    @Override
    public List<String> listObjects(String prefix) {
        logger.debug("Listing objects with prefix: {}", prefix);
        ListObjectsV2Response response = s3Client.listObjectsV2(
                ListObjectsV2Request.builder().bucket(bucketName).prefix(prefix).build()
        );
        return response.contents().stream()
                .map(S3Object::key)
                .collect(Collectors.toList());
    }
    
    @Override
    public ListObjectsResult listObjects(String prefix, int maxKeys, String startAfter) {
        logger.debug("Listing objects with prefix: {} maxKeys={} startAfter={}", prefix, maxKeys, startAfter);
        
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
                .map(key -> key.substring(prefix.length())) // Remove prefix
                .collect(Collectors.toList());
        
        String nextMarker = response.isTruncated() && !keys.isEmpty() ? 
                keys.get(keys.size() - 1) : null;
        
        return new ListObjectsResult(keys, nextMarker, response.isTruncated());
    }
}
