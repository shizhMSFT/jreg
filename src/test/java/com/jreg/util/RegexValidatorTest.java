package com.jreg.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegexValidatorTest {

    @Test
    void testValidRepositoryNames() {
        assertTrue(RegexValidator.isValidRepositoryName("myrepo"));
        assertTrue(RegexValidator.isValidRepositoryName("my-repo"));
        assertTrue(RegexValidator.isValidRepositoryName("my_repo"));
        assertTrue(RegexValidator.isValidRepositoryName("my__repo"));
        assertTrue(RegexValidator.isValidRepositoryName("org/repo"));
        assertTrue(RegexValidator.isValidRepositoryName("org/team/repo"));
        assertTrue(RegexValidator.isValidRepositoryName("library/ubuntu"));
        assertTrue(RegexValidator.isValidRepositoryName("a1b2c3"));
    }

    @Test
    void testInvalidRepositoryNames() {
        assertFalse(RegexValidator.isValidRepositoryName(""));
        assertFalse(RegexValidator.isValidRepositoryName(null));
        assertFalse(RegexValidator.isValidRepositoryName("MyRepo")); // uppercase
        assertFalse(RegexValidator.isValidRepositoryName("my repo")); // space
        assertFalse(RegexValidator.isValidRepositoryName("my/Repo")); // uppercase
        assertFalse(RegexValidator.isValidRepositoryName("-myrepo")); // starts with dash
        assertFalse(RegexValidator.isValidRepositoryName("myrepo-")); // ends with dash
        assertFalse(RegexValidator.isValidRepositoryName("my..repo")); // consecutive dots
    }

    @Test
    void testValidTagNames() {
        assertTrue(RegexValidator.isValidTagName("latest"));
        assertTrue(RegexValidator.isValidTagName("v1.0.0"));
        assertTrue(RegexValidator.isValidTagName("v1_0_0"));
        assertTrue(RegexValidator.isValidTagName("1234"));
        assertTrue(RegexValidator.isValidTagName("_tag"));
        assertTrue(RegexValidator.isValidTagName("TAG"));
        assertTrue(RegexValidator.isValidTagName("my-tag-123"));
    }

    @Test
    void testInvalidTagNames() {
        assertFalse(RegexValidator.isValidTagName(""));
        assertFalse(RegexValidator.isValidTagName(null));
        assertFalse(RegexValidator.isValidTagName("-tag")); // starts with dash
        assertFalse(RegexValidator.isValidTagName(".tag")); // starts with dot
        assertFalse(RegexValidator.isValidTagName("a".repeat(129))); // too long
        assertFalse(RegexValidator.isValidTagName("my tag")); // space
    }

    @Test
    void testValidDigests() {
        assertTrue(RegexValidator.isValidDigest(
                "sha256:b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
        ));
        assertTrue(RegexValidator.isValidDigest(
                "sha512:309ecc489c12d6eb4cc40f50c902f2b4d0ed77ee511a7c7a9bcd3ca86d4cd86f989dd35bc5ff499670da34255b45b0cfd830e81f605dcf7dc5542e93ae9cd76f"
        ));
    }

    @Test
    void testInvalidDigests() {
        assertFalse(RegexValidator.isValidDigest(""));
        assertFalse(RegexValidator.isValidDigest(null));
        assertFalse(RegexValidator.isValidDigest("sha256:invalid"));
        assertFalse(RegexValidator.isValidDigest("md5:abc123"));
        assertFalse(RegexValidator.isValidDigest("sha256:ABC123")); // uppercase
        assertFalse(RegexValidator.isValidDigest("abc123")); // no algorithm
    }

    @Test
    void testIsDigest() {
        assertTrue(RegexValidator.isDigest(
                "sha256:b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
        ));
        assertFalse(RegexValidator.isDigest("latest"));
        assertFalse(RegexValidator.isDigest("v1.0.0"));
    }
}
