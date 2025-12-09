package com.jreg.controller;

import com.azure.storage.blob.BlobServiceClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health check controller for Azure Blob Storage connectivity.
 */
@Component
public class HealthController implements HealthIndicator {
    
    private final BlobServiceClient blobServiceClient;
    private final String containerName;
    
    public HealthController(BlobServiceClient blobServiceClient, String blobContainerName) {
        this.blobServiceClient = blobServiceClient;
        this.containerName = blobContainerName;
    }
    
    @Override
    public Health health() {
        try {
            boolean exists = blobServiceClient.getBlobContainerClient(containerName).exists();
            if (exists) {
                return Health.up()
                        .withDetail("azure-blob-storage", "connected")
                        .withDetail("container", containerName)
                        .build();
            } else {
                return Health.down()
                        .withDetail("azure-blob-storage", "container not found")
                        .withDetail("container", containerName)
                        .build();
            }
        } catch (Exception e) {
            return Health.down()
                    .withDetail("azure-blob-storage", "disconnected")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
