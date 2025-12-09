package com.jreg.controller;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Health check controller for AWS S3 connectivity.
 */
@Component
public class HealthController implements HealthIndicator {
    
    private final S3Client s3Client;
    private final String bucketName;
    
    public HealthController(S3Client s3Client, String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }
    
    @Override
    public Health health() {
        try {
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.headBucket(headBucketRequest);
            
            return Health.up()
                    .withDetail("aws-s3", "connected")
                    .withDetail("bucket", bucketName)
                    .build();
        } catch (NoSuchBucketException e) {
            return Health.down()
                    .withDetail("aws-s3", "bucket not found")
                    .withDetail("bucket", bucketName)
                    .build();
        } catch (S3Exception e) {
            return Health.down()
                    .withDetail("aws-s3", "disconnected")
                    .withDetail("error", e.getMessage())
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("aws-s3", "disconnected")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
