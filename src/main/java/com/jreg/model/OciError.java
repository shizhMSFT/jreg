package com.jreg.model;

import java.util.List;
import java.util.Map;

/**
 * Represents an OCI-compliant error response.
 */
public record OciError(String code, String message, Map<String, Object> detail) {
    
    public OciError(String code, String message) {
        this(code, message, null);
    }
    
    /**
     * Create an error response wrapper with a list of errors
     */
    public static Map<String, Object> response(OciError... errors) {
        return Map.of("errors", List.of(errors));
    }
    
    /**
     * Create an error response wrapper with a single error
     */
    public static Map<String, Object> singleError(String code, String message) {
        return response(new OciError(code, message));
    }
    
    /**
     * Create an error response with detail
     */
    public static Map<String, Object> singleError(String code, String message, Map<String, Object> detail) {
        return response(new OciError(code, message, detail));
    }
}
