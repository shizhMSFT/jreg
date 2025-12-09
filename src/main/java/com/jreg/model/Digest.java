package com.jreg.model;

/**
 * Represents a content identifier using cryptographic hash.
 * Format: algorithm:hex (e.g., sha256:abc123...)
 */
public record Digest(String algorithm, String hex) {
    
    public Digest {
        if (algorithm == null || (!algorithm.equals("sha256") && !algorithm.equals("sha512"))) {
            throw new IllegalArgumentException("Algorithm must be sha256 or sha512");
        }
        if (hex == null || !hex.matches("[a-f0-9]{64,128}")) {
            throw new IllegalArgumentException("Invalid hex digest format");
        }
    }
    
    @Override
    public String toString() {
        return algorithm + ":" + hex;
    }
    
    /**
     * Parse a digest string in format "algorithm:hex"
     */
    public static Digest parse(String digestString) {
        if (digestString == null || !digestString.contains(":")) {
            throw new IllegalArgumentException("Invalid digest format. Expected: algorithm:hex");
        }
        String[] parts = digestString.split(":", 2);
        return new Digest(parts[0], parts[1]);
    }
}
