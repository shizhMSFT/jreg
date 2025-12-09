package com.jreg.util;

import com.jreg.model.Digest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class S3KeyGeneratorTest {

    @Test
    void testBlobKey() {
        Digest digest = new Digest("sha256", "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890");
        
        String key = S3KeyGenerator.blobKey(digest);
        
        assertEquals("blobs/sha256/ab/abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890", key);
    }

    @Test
    void testManifestKey() {
        Digest digest = new Digest("sha256", "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
        
        String key = S3KeyGenerator.manifestKey("myorg/myrepo", digest);
        
        assertEquals("manifests/myorg/myrepo/sha256/1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef", key);
    }

    @Test
    void testTagKey() {
        String key = S3KeyGenerator.tagKey("library/ubuntu", "latest");
        
        assertEquals("tags/library/ubuntu/latest", key);
    }

    @Test
    void testUploadMetadataKey() {
        String sessionId = "550e8400-e29b-41d4-a716-446655440000";
        
        String key = S3KeyGenerator.uploadMetadataKey(sessionId);
        
        assertEquals("uploads/550e8400-e29b-41d4-a716-446655440000/metadata.json", key);
    }

    @Test
    void testUploadChunkKey() {
        String sessionId = "550e8400-e29b-41d4-a716-446655440000";
        
        String key = S3KeyGenerator.uploadChunkKey(sessionId, 0, 1048575);
        
        assertEquals("uploads/550e8400-e29b-41d4-a716-446655440000/chunks/0-1048575", key);
    }

    @Test
    void testReferrersKey() {
        Digest digest = new Digest("sha256", "fedcba0987654321fedcba0987654321fedcba0987654321fedcba0987654321");
        
        String key = S3KeyGenerator.referrersKey("myorg/myrepo", digest);
        
        assertEquals("referrers/myorg/myrepo/sha256/fedcba0987654321fedcba0987654321fedcba0987654321fedcba0987654321.json", key);
    }

    @Test
    void testTagListPrefix() {
        String prefix = S3KeyGenerator.tagListPrefix("library/ubuntu");
        
        assertEquals("tags/library/ubuntu/", prefix);
    }

    @Test
    void testManifestListPrefix() {
        String prefix = S3KeyGenerator.manifestListPrefix("library/ubuntu");
        
        assertEquals("manifests/library/ubuntu/", prefix);
    }
}
