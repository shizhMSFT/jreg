package com.jreg.util;

import com.jreg.model.Digest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DigestCalculatorTest {

    @Test
    void testCalculateSha256FromBytes() {
        String testData = "hello world";
        byte[] bytes = testData.getBytes(StandardCharsets.UTF_8);
        
        Digest digest = DigestCalculator.calculateSha256(bytes);
        
        assertEquals("sha256", digest.algorithm());
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", 
                     digest.hex());
    }

    @Test
    void testCalculateSha256FromStream() {
        String testData = "hello world";
        ByteArrayInputStream stream = new ByteArrayInputStream(
                testData.getBytes(StandardCharsets.UTF_8)
        );
        
        Digest digest = DigestCalculator.calculateSha256(stream);
        
        assertEquals("sha256", digest.algorithm());
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", 
                     digest.hex());
    }

    @Test
    void testCalculateSha512() {
        String testData = "hello world";
        ByteArrayInputStream stream = new ByteArrayInputStream(
                testData.getBytes(StandardCharsets.UTF_8)
        );
        
        Digest digest = DigestCalculator.calculateSha512(stream);
        
        assertEquals("sha512", digest.algorithm());
        assertTrue(digest.hex().length() > 64);
    }

    @Test
    void testEmptyData() {
        byte[] emptyBytes = new byte[0];
        
        Digest digest = DigestCalculator.calculateSha256(emptyBytes);
        
        assertEquals("sha256", digest.algorithm());
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 
                     digest.hex());
    }
}
