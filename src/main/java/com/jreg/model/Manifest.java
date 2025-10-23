package com.jreg.model;

import java.time.Instant;

/**
 * Represents a JSON document describing an image or artifact.
 */
public class Manifest {
    private Digest digest;
    private String repository;
    private String mediaType;
    private int schemaVersion;
    private byte[] content; // Raw manifest JSON bytes
    private long size;
    private Digest subject; // Optional reference to another manifest
    private Instant uploadedAt;

    public Manifest() {
    }

    public Manifest(Digest digest, String repository, String mediaType, byte[] content) {
        this.digest = digest;
        this.repository = repository;
        this.mediaType = mediaType;
        this.content = content;
        this.size = content.length;
        this.uploadedAt = Instant.now();
    }

    public Digest getDigest() {
        return digest;
    }

    public void setDigest(Digest digest) {
        this.digest = digest;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
        this.size = content != null ? content.length : 0;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Digest getSubject() {
        return subject;
    }

    public void setSubject(Digest subject) {
        this.subject = subject;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
