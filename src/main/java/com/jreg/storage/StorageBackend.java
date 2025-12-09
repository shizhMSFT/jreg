package com.jreg.storage;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Interface for storage backend operations.
 * Abstracts the underlying storage implementation (Blob Storage, filesystem, etc.)
 */
public interface StorageBackend {
    
    /**
     * Get an object as an input stream
     */
    InputStream getObject(String key);
    
    /**
     * Get an object with range support
     */
    InputStream getObjectRange(String key, String range);
    
    /**
     * Put an object with content
     */
    void putObject(String key, byte[] content, String contentType);
    
    /**
     * Put an object with input stream
     */
    void putObject(String key, InputStream content, long contentLength, String contentType);
    
    /**
     * Put an object with metadata
     */
    void putObject(String key, byte[] content, String contentType, Map<String, String> metadata);
    
    /**
     * Check if an object exists
     */
    boolean objectExists(String key);
    
    /**
     * Get object metadata
     */
    Map<String, String> getObjectMetadata(String key);
    
    /**
     * Get object size
     */
    long getObjectSize(String key);
    
    /**
     * Delete an object
     */
    void deleteObject(String key);
    
    /**
     * List objects with prefix
     */
    List<String> listObjects(String prefix);
    
    /**
     * List objects with prefix and pagination
     */
    ListObjectsResult listObjects(String prefix, int maxKeys, String startAfter);
    
    /**
     * Result of list objects operation
     */
    record ListObjectsResult(List<String> keys, String nextMarker, boolean isTruncated) {}
}
