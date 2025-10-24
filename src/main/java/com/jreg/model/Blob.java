package com.jreg.model;

import java.time.Instant;

/**
 * Represents binary content (image layers, config files) identified by digest.
 */
public class Blob {
    private Digest digest;
    private long size;
    private String mediaType;
    private Instant uploadedAt;
    private String blobName;

    public Blob() {
    }

    public Blob(Digest digest, long size, String mediaType, String blobName) {
        this.digest = digest;
        this.size = size;
        this.mediaType = mediaType;
        this.uploadedAt = Instant.now();
        this.blobName = blobName;
    }

    public Digest getDigest() {
        return digest;
    }

    public void setDigest(Digest digest) {
        this.digest = digest;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getBlobName() {
        return blobName;
    }

    public void setBlobName(String blobName) {
        this.blobName = blobName;
    }
}
