package com.jreg.exception;

import com.jreg.model.Digest;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a blob is not found in storage.
 * Maps to OCI error code BLOB_UNKNOWN.
 */
public class BlobNotFoundException extends OciException {
    
    public BlobNotFoundException(Digest digest) {
        super("BLOB_UNKNOWN", 
              "Blob " + digest + " not found", 
              HttpStatus.NOT_FOUND.value());
    }

    public BlobNotFoundException(String repository, Digest digest) {
        super("BLOB_UNKNOWN", 
              "Blob " + digest + " not found in repository " + repository, 
              HttpStatus.NOT_FOUND.value());
    }
}
