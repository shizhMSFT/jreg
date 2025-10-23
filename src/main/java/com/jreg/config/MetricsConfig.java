package com.jreg.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter blobUploadsTotal(MeterRegistry registry) {
        return Counter.builder("jreg.blob.uploads.total")
                .description("Total number of blob uploads")
                .register(registry);
    }

    @Bean
    public Counter blobUploadBytesTotal(MeterRegistry registry) {
        return Counter.builder("jreg.blob.upload.bytes.total")
                .description("Total bytes uploaded for blobs")
                .baseUnit("bytes")
                .register(registry);
    }

    @Bean
    public Timer blobUploadDuration(MeterRegistry registry) {
        return Timer.builder("jreg.blob.upload.duration.seconds")
                .description("Blob upload duration")
                .register(registry);
    }

    @Bean
    public Counter manifestPushesTotal(MeterRegistry registry) {
        return Counter.builder("jreg.manifest.pushes.total")
                .description("Total number of manifest pushes")
                .register(registry);
    }

    @Bean
    public Timer manifestPushDuration(MeterRegistry registry) {
        return Timer.builder("jreg.manifest.push.duration.seconds")
                .description("Manifest push duration")
                .register(registry);
    }
}
