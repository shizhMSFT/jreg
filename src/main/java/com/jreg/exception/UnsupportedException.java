package com.jreg.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an operation is not supported.
 * Maps to OCI error code UNSUPPORTED.
 */
public class UnsupportedException extends OciException {
    
    public UnsupportedException(String message) {
        super("UNSUPPORTED", message, HttpStatus.BAD_REQUEST.value());
    }
}
