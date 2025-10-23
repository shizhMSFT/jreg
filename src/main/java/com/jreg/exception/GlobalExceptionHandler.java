package com.jreg.exception;

import com.jreg.model.OciError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Global exception handler that maps exceptions to OCI error responses.
 * Extracts contextual information (repository, digest, tag) for better debugging.
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    // Patterns to extract context from exception messages
    private static final Pattern REPOSITORY_PATTERN = Pattern.compile("repository\\s+([a-z0-9]+(?:[._-][a-z0-9]+)*)");
    private static final Pattern DIGEST_PATTERN = Pattern.compile("(sha256:[a-f0-9]{64}|sha512:[a-f0-9]{128})");
    private static final Pattern TAG_PATTERN = Pattern.compile("tag\\s+([a-zA-Z0-9_][a-zA-Z0-9._-]{0,127})");
    
    @ExceptionHandler(OciException.class)
    public ResponseEntity<Map<String, Object>> handleOciException(OciException ex, WebRequest request) {
        String requestUri = extractRequestUri(request);
        logger.warn("OCI exception: code={}, message={}, uri={}", 
                    ex.getErrorCode(), ex.getMessage(), requestUri);
        
        Map<String, Object> detail = extractContextFromMessage(ex.getMessage(), requestUri);
        
        Map<String, Object> response = OciError.singleError(
                ex.getErrorCode(),
                ex.getMessage(),
                detail.isEmpty() ? null : detail
        );
        
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(response);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        String requestUri = extractRequestUri(request);
        logger.warn("Invalid argument: {}, uri={}", ex.getMessage(), requestUri);
        
        Map<String, Object> detail = extractContextFromMessage(ex.getMessage(), requestUri);
        
        Map<String, Object> response = OciError.singleError(
                "INVALID_PARAMETER",
                ex.getMessage(),
                detail.isEmpty() ? null : detail
        );
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex, WebRequest request) {
        String requestUri = extractRequestUri(request);
        logger.error("Unexpected error: uri={}", requestUri, ex);
        
        Map<String, Object> detail = Map.of("uri", requestUri);
        
        Map<String, Object> response = OciError.singleError(
                "UNKNOWN",
                "An unexpected error occurred: " + ex.getMessage(),
                detail
        );
        
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
    
    /**
     * Extract contextual information from exception message and request URI
     */
    private Map<String, Object> extractContextFromMessage(String message, String uri) {
        Map<String, Object> detail = new HashMap<>();
        
        if (message != null) {
            // Extract repository name
            Matcher repoMatcher = REPOSITORY_PATTERN.matcher(message);
            if (repoMatcher.find()) {
                detail.put("repository", repoMatcher.group(1));
            }
            
            // Extract digest
            Matcher digestMatcher = DIGEST_PATTERN.matcher(message);
            if (digestMatcher.find()) {
                detail.put("digest", digestMatcher.group(1));
            }
            
            // Extract tag
            Matcher tagMatcher = TAG_PATTERN.matcher(message);
            if (tagMatcher.find()) {
                detail.put("tag", tagMatcher.group(1));
            }
        }
        
        // Add request URI for context
        if (uri != null && !uri.isEmpty()) {
            detail.put("uri", uri);
        }
        
        return detail;
    }
    
    /**
     * Extract request URI from WebRequest
     */
    private String extractRequestUri(WebRequest request) {
        try {
            return request.getDescription(false).replace("uri=", "");
        } catch (Exception e) {
            return null;
        }
    }
}
