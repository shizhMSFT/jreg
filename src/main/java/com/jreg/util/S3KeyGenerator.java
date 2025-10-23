package com.jreg.util;

import com.jreg.model.Digest;

/**
 * Generates S3 object keys for different entity types.
 * Implements the S3 key structure defined in the data model.
 */
public class S3KeyGenerator {
    
    /**
     * Generate S3 key for a blob
     * Format: blobs/{algorithm}/{first-2-chars}/{hex}
     */
    public static String blobKey(Digest digest) {
        String hex = digest.hex();
        String prefix = hex.substring(0, 2);
        return String.format("blobs/%s/%s/%s", digest.algorithm(), prefix, hex);
    }
    
    /**
     * Generate S3 key for a manifest
     * Format: manifests/{repository}/{algorithm}/{digest}
     */
    public static String manifestKey(String repository, Digest digest) {
        return String.format("manifests/%s/%s/%s", repository, digest.algorithm(), digest.hex());
    }
    
    /**
     * Generate S3 key for a tag
     * Format: tags/{repository}/{tag-name}
     */
    public static String tagKey(String repository, String tagName) {
        return String.format("tags/%s/%s", repository, tagName);
    }
    
    /**
     * Generate S3 key for upload session metadata
     * Format: uploads/{session-id}/metadata.json
     */
    public static String uploadMetadataKey(String sessionId) {
        return String.format("uploads/%s/metadata.json", sessionId);
    }
    
    /**
     * Generate S3 key for an upload chunk
     * Format: uploads/{session-id}/chunks/{start}-{end}
     */
    public static String uploadChunkKey(String sessionId, long start, long end) {
        return String.format("uploads/%s/chunks/%d-%d", sessionId, start, end);
    }
    
    /**
     * Generate S3 key for referrers index
     * Format: referrers/{repository}/{algorithm}/{digest}.json
     */
    public static String referrersKey(String repository, Digest digest) {
        return String.format("referrers/%s/%s/%s.json", repository, digest.algorithm(), digest.hex());
    }
    
    /**
     * Generate S3 prefix for listing tags in a repository
     * Format: tags/{repository}/
     */
    public static String tagListPrefix(String repository) {
        return String.format("tags/%s/", repository);
    }
    
    /**
     * Generate S3 prefix for listing manifests in a repository
     * Format: manifests/{repository}/
     */
    public static String manifestListPrefix(String repository) {
        return String.format("manifests/%s/", repository);
    }
}
