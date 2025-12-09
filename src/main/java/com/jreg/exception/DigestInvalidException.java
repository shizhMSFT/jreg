package com.jreg.exception;

/**
 * Exception thrown when digest validation fails.
 * Maps to OCI error code: DIGEST_INVALID
 */
public class DigestInvalidException extends OciException {
    
    public DigestInvalidException(String message) {
        super("DIGEST_INVALID", message, 400);
    }
    
    public DigestInvalidException(String message, Throwable cause) {
        super("DIGEST_INVALID", message, 400, cause);
    }
}
