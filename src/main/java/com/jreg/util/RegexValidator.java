package com.jreg.util;

import java.util.regex.Pattern;

/**
 * Validates OCI entity names against specification regex patterns.
 */
public class RegexValidator {
    public static boolean isValidRepository(String repository) {
        return repository != null && repository.matches("^[a-z0-9]+(?:[._-][a-z0-9]+)*(?:/[a-z0-9]+(?:[._-][a-z0-9]+)*)*$");
    }

    public static boolean isValidTag(String tag) {
        return tag != null && tag.matches("^[A-Za-z0-9_][A-Za-z0-9._-]{0,127}$");
    }
    
    // OCI Distribution Spec repository name pattern
    private static final Pattern REPOSITORY_PATTERN = Pattern.compile(
            "^[a-z0-9]+(?:(?:\\.|_|__|-+)[a-z0-9]+)*(?:/[a-z0-9]+(?:(?:\\.|_|__|-+)[a-z0-9]+)*)*$"
    );
    
    // OCI Distribution Spec tag name pattern
    private static final Pattern TAG_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_][a-zA-Z0-9._-]{0,127}$"
    );
    
    // Digest pattern (algorithm:hex)
    private static final Pattern DIGEST_PATTERN = Pattern.compile(
            "^(sha256|sha512):[a-f0-9]{64,128}$"
    );
    
    /**
     * Validate repository name against OCI spec
     */
    public static boolean isValidRepositoryName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return REPOSITORY_PATTERN.matcher(name).matches();
    }
    
    /**
     * Validate tag name against OCI spec
     */
    public static boolean isValidTagName(String tag) {
        if (tag == null || tag.isEmpty() || tag.length() > 128) {
            return false;
        }
        return TAG_PATTERN.matcher(tag).matches();
    }
    
    /**
     * Validate digest format
     */
    public static boolean isValidDigest(String digest) {
        if (digest == null || digest.isEmpty()) {
            return false;
        }
        return DIGEST_PATTERN.matcher(digest).matches();
    }
    
    /**
     * Check if reference is a digest (vs a tag)
     */
    public static boolean isDigest(String reference) {
        return isValidDigest(reference);
    }
}
