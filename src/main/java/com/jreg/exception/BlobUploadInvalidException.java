package com.jreg.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when blob upload parameters are invalid.
 * Maps to OCI error code BLOB_UPLOAD_INVALID.
 */
public class BlobUploadInvalidException extends OciException {
    
    public BlobUploadInvalidException(String message) {
        super("BLOB_UPLOAD_INVALID", message, HttpStatus.BAD_REQUEST.value());
    }
}
