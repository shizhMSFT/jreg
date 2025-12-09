package com.jreg.service;

import com.jreg.exception.BlobUploadInvalidException;
import com.jreg.exception.BlobUploadUnknownException;
import com.jreg.model.ByteRange;
import com.jreg.model.UploadSession;
import com.jreg.storage.StorageBackend;
import com.jreg.util.S3KeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages chunked blob upload sessions.
 */
@Service
public class UploadSessionService {
    private static final Logger logger = LoggerFactory.getLogger(UploadSessionService.class);
    private static final Duration SESSION_TIMEOUT = Duration.ofHours(24);
    
    private final Map<UUID, UploadSession> activeSessions = new ConcurrentHashMap<>();
    private final StorageBackend storage;
    private final ValidationService validationService;

    public UploadSessionService(StorageBackend storage, ValidationService validationService) {
        this.storage = storage;
        this.validationService = validationService;
    }

    /**
     * Start a new upload session
     */
    public UploadSession startSession(String repository) {
        validationService.validateRepositoryName(repository);
        
        UUID sessionId = UUID.randomUUID();
        UploadSession session = new UploadSession(sessionId, repository);
        
        activeSessions.put(sessionId, session);

        MDC.put("repository", repository);
        MDC.put("session_id", sessionId.toString());
        logger.info("Started upload session");
        MDC.clear();

        return session;
    }

    /**
     * Get an existing session
     */
    public UploadSession getSession(UUID sessionId) {
        UploadSession session = activeSessions.get(sessionId);
        if (session == null) {
            throw new BlobUploadUnknownException(sessionId);
        }

        // Check if session expired
        if (Duration.between(session.getLastActivityAt(), Instant.now()).compareTo(SESSION_TIMEOUT) > 0) {
            activeSessions.remove(sessionId);
            cleanupSessionData(sessionId);
            throw new BlobUploadUnknownException(sessionId);
        }

        return session;
    }

    /**
     * Upload a chunk to a session
     */
    public void uploadChunk(UUID sessionId, InputStream chunk, long startByte, long endByte) {
        UploadSession session = getSession(sessionId);

        // Validate range
        if (startByte < 0 || endByte < startByte) {
            throw new BlobUploadInvalidException("Invalid byte range: " + startByte + "-" + endByte);
        }

        // Verify sequential upload (OCI spec requirement)
        long expectedStart = session.getLastUploadedByte() + 1;
        if (session.getLastUploadedByte() >= 0 && startByte != expectedStart) {
            throw new BlobUploadInvalidException(
                "Non-sequential upload: expected start " + expectedStart + " but got " + startByte);
        }

    // Store chunk
    String chunkKey = S3KeyGenerator.uploadChunkKey(sessionId.toString(), startByte, endByte);
        byte[] chunkBytes;
        try {
            chunkBytes = chunk.readAllBytes();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read chunk content: " + e.getMessage(), e);
        }
        storage.putObject(chunkKey, new java.io.ByteArrayInputStream(chunkBytes), chunkBytes.length, "application/octet-stream");

        // Update session
        ByteRange range = new ByteRange(startByte, endByte);
        session.addUploadedRange(range);

        MDC.put("session_id", sessionId.toString());
        MDC.put("repository", session.getRepository());
        logger.debug("Uploaded chunk: {} bytes ({}-{})", range.size(), startByte, endByte);
        MDC.clear();
    }

    /**
     * Get the current upload status
     */
    public UploadSession getStatus(UUID sessionId) {
        return getSession(sessionId);
    }

    /**
     * Complete an upload session and assemble chunks
     */
    public InputStream completeSession(UUID sessionId) {
        UploadSession session = getSession(sessionId);

        try {
            // Assemble chunks in order
            byte[] assembledData = assembleChunks(session);
            
            // Clean up session
            activeSessions.remove(sessionId);
            cleanupSessionData(sessionId);

            MDC.put("session_id", sessionId.toString());
            MDC.put("repository", session.getRepository());
            logger.info("Completed upload session: {} bytes", assembledData.length);
            MDC.clear();

            return new ByteArrayInputStream(assembledData);
            
        } catch (Exception e) {
            logger.error("Failed to complete upload session {}", sessionId, e);
            throw new BlobUploadInvalidException("Failed to assemble chunks: " + e.getMessage());
        }
    }

    /**
     * Cancel an upload session
     */
    public void cancelSession(UUID sessionId) {
        UploadSession session = getSession(sessionId);
        
        activeSessions.remove(sessionId);
        cleanupSessionData(sessionId);

        MDC.put("session_id", sessionId.toString());
        MDC.put("repository", session.getRepository());
        logger.info("Cancelled upload session");
        MDC.clear();
    }

    /**
     * Assemble all chunks into a single byte array
     */
    private byte[] assembleChunks(UploadSession session) {
        long totalSize = session.getTotalUploadedBytes();
        byte[] result = new byte[(int) totalSize];
        
        int offset = 0;
        for (ByteRange range : session.getUploadedRanges()) {
            String chunkKey = S3KeyGenerator.uploadChunkKey(session.getSessionId().toString(), range.start(), range.end());
            try (InputStream chunkStream = storage.getObject(chunkKey)) {
                byte[] chunkData = chunkStream.readAllBytes();
                System.arraycopy(chunkData, 0, result, offset, chunkData.length);
                offset += chunkData.length;
            } catch (Exception e) {
                throw new RuntimeException("Failed to read chunk at " + range.start(), e);
            }
        }

        return result;
    }

    /**
     * Delete all chunk data for a session
     */
    private void cleanupSessionData(UUID sessionId) {
        String prefix = "uploads/" + sessionId + "/";
        storage.listObjects(prefix).forEach(key -> {
            storage.deleteObject(key);
            logger.debug("Deleted chunk: {}", key);
        });
    }

    /**
     * Clean up expired sessions (should be called periodically)
     */
    public void cleanupExpiredSessions() {
        Instant now = Instant.now();
        activeSessions.entrySet().removeIf(entry -> {
            UploadSession session = entry.getValue();
            if (Duration.between(session.getLastActivityAt(), now).compareTo(SESSION_TIMEOUT) > 0) {
                cleanupSessionData(entry.getKey());
                logger.info("Cleaned up expired session: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }
}
