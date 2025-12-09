package com.jreg.model;

import java.time.Instant;

/**
 * Represents a human-readable pointer to a manifest.
 */
public class Tag {
    private String repository;
    private String name;
    private Digest manifestDigest;
    private Instant updatedAt;

    public Tag() {
    }

    public Tag(String repository, String name, Digest manifestDigest) {
        this.repository = repository;
        this.name = name;
        this.manifestDigest = manifestDigest;
        this.updatedAt = Instant.now();
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Digest getManifestDigest() {
        return manifestDigest;
    }

    public void setManifestDigest(Digest manifestDigest) {
        this.manifestDigest = manifestDigest;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
