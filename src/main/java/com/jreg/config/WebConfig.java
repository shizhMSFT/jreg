package com.jreg.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/v2/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "HEAD", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(
                        "Docker-Content-Digest",
                        "Docker-Distribution-API-Version",
                        "Docker-Upload-UUID",
                        "Location",
                        "Range",
                        "Content-Range",
                        "Link",
                        "OCI-Subject",
                        "OCI-Filters-Applied"
                );
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // Disable PathPatternParser to use AntPathMatcher which supports {name:.+} regex
        configurer.setPatternParser(null);
        configurer.setUseTrailingSlashMatch(true);
    }
}
