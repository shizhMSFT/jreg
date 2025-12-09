package com.jreg.config;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Azure Blob Storage client.
 */
@Configuration
public class BlobStorageConfig {

    @Value("${azure.storage.container-name}")
    private String containerName;

    @Value("${azure.storage.endpoint:}")
    private String endpoint;

    @Value("${azure.storage.connection-string:}")
    private String connectionString;

    @Bean
    BlobServiceClient blobServiceClient() {
        BlobServiceClientBuilder builder = new BlobServiceClientBuilder();

        // Use connection string if provided (local development with Azurite)
        if (!connectionString.isEmpty()) {
            builder.connectionString(connectionString);
        } else if (!endpoint.isEmpty()) {
            // Use endpoint with DefaultAzureCredential for production
            builder.endpoint(endpoint)
                   .credential(new DefaultAzureCredentialBuilder().build());
        } else {
            throw new IllegalStateException(
                "Azure Blob Storage configuration missing: Either azure.storage.connection-string or azure.storage.endpoint must be provided"
            );
        }

        return builder.buildClient();
    }

    @Bean
    String blobContainerName() {
        return containerName;
    }
}
