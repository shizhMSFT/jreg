package com.jreg.exception;

/**
 * Exception thrown when repository or tag name validation fails.
 * Maps to OCI error code: NAME_INVALID
 */
public class NameInvalidException extends OciException {
    
    public NameInvalidException(String message) {
        super("NAME_INVALID", message, 400);
    }
    
    public NameInvalidException(String message, Throwable cause) {
        super("NAME_INVALID", message, 400, cause);
    }
}
