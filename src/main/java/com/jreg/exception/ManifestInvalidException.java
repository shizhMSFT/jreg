package com.jreg.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when manifest JSON is malformed or invalid.
 * Maps to OCI error code MANIFEST_INVALID.
 */
public class ManifestInvalidException extends OciException {
    
    public ManifestInvalidException(String message) {
        super("MANIFEST_INVALID", message, HttpStatus.BAD_REQUEST.value());
    }

    public ManifestInvalidException(String message, Throwable cause) {
        super("MANIFEST_INVALID", message, HttpStatus.BAD_REQUEST.value());
        initCause(cause);
    }
}
