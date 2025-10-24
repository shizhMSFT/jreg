package com.jreg.model;

/**
 * Represents a byte range for chunked uploads.
 * Format: start-end (inclusive)
 */
public record ByteRange(long start, long end) {
    
    public ByteRange {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid byte range: start=" + start + ", end=" + end);
        }
    }
    
    /**
     * Calculate the size of this range
     */
    public long size() {
        return end - start + 1;
    }
    
    /**
     * Parse a Content-Range header value
     * Supports formats: "bytes=0-1023" or "0-1023"
     */
    public static ByteRange parse(String rangeHeader) {
        if (rangeHeader == null) {
            throw new IllegalArgumentException("Range header cannot be null");
        }
        
        String range = rangeHeader.replace("bytes=", "").replace("bytes ", "").trim();
        String[] parts = range.split("-");
        
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid range format: " + rangeHeader);
        }
        
        try {
            long start = Long.parseLong(parts[0].trim());
            long end = Long.parseLong(parts[1].trim());
            return new ByteRange(start, end);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid range numbers: " + rangeHeader, e);
        }
    }
    
    /**
     * Format as Content-Range header value
     */
    public String toContentRange(long totalSize) {
        return "bytes %d-%d/%d".formatted(start, end, totalSize);
    }
    
    /**
     * Format as Range header value
     */
    @Override
    public String toString() {
        return start + "-" + end;
    }
}
