package com.jreg.controller;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

/**
 * Health check controller for S3 connectivity.
 */
@Component
public class HealthController implements HealthIndicator {
    
    private final S3Client s3Client;
    private final String bucketName;
    
    public HealthController(S3Client s3Client, String s3BucketName) {
        this.s3Client = s3Client;
        this.bucketName = s3BucketName;
    }
    
    @Override
    public Health health() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            return Health.up()
                    .withDetail("s3", "connected")
                    .withDetail("bucket", bucketName)
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("s3", "disconnected")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
