package com.jreg.exception;

import org.springframework.http.HttpStatus;
import java.util.UUID;

/**
 * Thrown when an upload session is not found.
 * Maps to OCI error code BLOB_UPLOAD_UNKNOWN.
 */
public class BlobUploadUnknownException extends OciException {
    
    public BlobUploadUnknownException(UUID sessionId) {
        super("BLOB_UPLOAD_UNKNOWN", 
              "Upload session " + sessionId + " not found", 
              HttpStatus.NOT_FOUND.value());
    }
}
