package com.jreg.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.s3.S3Client;

import static org.mockito.Mockito.mock;

/**
 * Test configuration that provides a mock S3Client for testing without LocalStack.
 */
@TestConfiguration
public class TestS3Config {

    @Bean
    @Primary
    public S3Client mockS3Client() {
        return mock(S3Client.class);
    }
}
