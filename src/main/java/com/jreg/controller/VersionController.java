package com.jreg.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Version controller implementing OCI Distribution Spec end-1.
 * GET /v2/ - Check API version
 */
@RestController
@RequestMapping("/v2")
public class VersionController {
    
    @GetMapping("/")
    public ResponseEntity<Map<String, String>> getApiVersion() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .header("Docker-Distribution-API-Version", "registry/2.0")
                .body(Map.of("version", "1.0.0"));
    }
}
