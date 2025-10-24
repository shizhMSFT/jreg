package com.jreg.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tracks state for chunked blob uploads.
 */
public class UploadSession {
    private UUID sessionId;
    private String repository;
    private List<ByteRange> uploadedRanges;
    private Long totalSize; // Optional
    private Instant createdAt;
    private Instant lastActivityAt;
    private String contentType;
    private String uploadId; // For Azure block blob upload

    public UploadSession() {
        this.uploadedRanges = new ArrayList<>();
    }

    public UploadSession(UUID sessionId, String repository) {
        this.sessionId = sessionId;
        this.repository = repository;
        this.uploadedRanges = new ArrayList<>();
        this.createdAt = Instant.now();
        this.lastActivityAt = Instant.now();
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public List<ByteRange> getUploadedRanges() {
        return uploadedRanges;
    }

    public void setUploadedRanges(List<ByteRange> uploadedRanges) {
        this.uploadedRanges = uploadedRanges;
    }

    public void addUploadedRange(ByteRange range) {
        this.uploadedRanges.add(range);
        this.lastActivityAt = Instant.now();
    }

    public Long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(Long totalSize) {
        this.totalSize = totalSize;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(Instant lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    /**
     * Get the last uploaded byte position
     */
    public long getLastUploadedByte() {
        if (uploadedRanges.isEmpty()) {
            return -1;
        }
        return uploadedRanges.get(uploadedRanges.size() - 1).end();
    }

    /**
     * Get total uploaded bytes
     */
    public long getTotalUploadedBytes() {
        return uploadedRanges.stream()
                .mapToLong(ByteRange::size)
                .sum();
    }
}
