package com.jreg.exception;

import com.jreg.model.Digest;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a manifest is not found in storage.
 * Maps to OCI error code MANIFEST_UNKNOWN.
 */
public class ManifestNotFoundException extends OciException {
    
    public ManifestNotFoundException(String repository, Digest digest) {
        super("MANIFEST_UNKNOWN", 
              "Manifest " + digest + " not found in repository " + repository, 
              HttpStatus.NOT_FOUND.value());
    }

    public ManifestNotFoundException(String repository, String reference) {
        super("MANIFEST_UNKNOWN", 
              "Manifest " + reference + " not found in repository " + repository, 
              HttpStatus.NOT_FOUND.value());
    }
}
