package com.jreg.util;

import com.jreg.model.Digest;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for calculating cryptographic digests.
 * Supports streaming digest calculation for large files.
 */
public class DigestCalculator {
    
    private static final int BUFFER_SIZE = 8192;
    
    /**
     * Calculate SHA-256 digest from input stream
     */
    public static Digest calculateSha256(InputStream inputStream) {
        return calculateDigest(inputStream, "SHA-256");
    }
    
    /**
     * Calculate SHA-512 digest from input stream
     */
    public static Digest calculateSha512(InputStream inputStream) {
        return calculateDigest(inputStream, "SHA-512");
    }
    
    /**
     * Calculate digest from byte array
     */
    public static Digest calculateSha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            return new Digest("sha256", bytesToHex(hash));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * Calculate digest from input stream with streaming support
     */
    private static Digest calculateDigest(InputStream inputStream, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            
            byte[] hash = md.digest();
            String algorithmName = algorithm.equals("SHA-256") ? "sha256" : "sha512";
            
            return new Digest(algorithmName, bytesToHex(hash));
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate digest", e);
        }
    }
    
    /**
     * Convert byte array to hexadecimal string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
