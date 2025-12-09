package com.jreg.storage;

import com.azure.core.http.rest.PagedResponse;
import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.*;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Azure Blob Storage implementation of the storage backend.
 */
@Component
public class BlobStorageBackend implements StorageBackend {
    
    private static final Logger logger = LoggerFactory.getLogger(BlobStorageBackend.class);
    
    private final String containerName;
    private final BlobContainerClient containerClient;
    
    public BlobStorageBackend(BlobServiceClient blobServiceClient, String blobContainerName) {
        this.containerName = blobContainerName;
        this.containerClient = blobServiceClient.getBlobContainerClient(containerName);
        ensureContainerExists();
    }
    
    private void ensureContainerExists() {
        try {
            if (containerClient.exists()) {
                logger.info("Blob container exists: {}", containerName);
            } else {
                logger.info("Creating blob container: {}", containerName);
                containerClient.create();
            }
        } catch (BlobStorageException e) {
            if (e.getErrorCode() == BlobErrorCode.CONTAINER_ALREADY_EXISTS) {
                logger.info("Blob container already exists: {}", containerName);
            } else {
                throw e;
            }
        }
    }
    
    @Override
    public InputStream getObject(String key) {
        logger.debug("Getting blob: {}", key);
        BlobClient blobClient = containerClient.getBlobClient(key);
        return blobClient.downloadContent().toStream();
    }
    
    @Override
    public InputStream getObjectRange(String key, String range) {
        logger.debug("Getting blob range: {} range={}", key, range);
        BlobClient blobClient = containerClient.getBlobClient(key);
        
        // Parse range header: "bytes=start-end"
        BlobRange blobRange = parseRange(range);
        
        BinaryData data = blobClient.downloadContentWithResponse(
            null, null, blobRange, false, null, null
        ).getValue();
        
        return data.toStream();
    }
    
    private BlobRange parseRange(String range) {
        // Parse "bytes=start-end" format
        if (range != null && range.startsWith("bytes=")) {
            String[] parts = range.substring(6).split("-");
            long start = Long.parseLong(parts[0]);
            if (parts.length > 1 && !parts[1].isEmpty()) {
                long end = Long.parseLong(parts[1]);
                long count = end - start + 1;
                return new BlobRange(start, count);
            } else {
                return new BlobRange(start);
            }
        }
        return null;
    }
    
    @Override
    public void putObject(String key, byte[] content, String contentType) {
        putObject(key, content, contentType, Map.of());
    }
    
    @Override
    public void putObject(String key, InputStream content, long contentLength, String contentType) {
        logger.debug("Putting blob: {} size={}", key, contentLength);
        BlobClient blobClient = containerClient.getBlobClient(key);
        
        BlobHttpHeaders headers = new BlobHttpHeaders().setContentType(contentType);
        BlobParallelUploadOptions options = new BlobParallelUploadOptions(content);
        options.setHeaders(headers);
        
        blobClient.uploadWithResponse(options, null, null);
    }
    
    @Override
    public void putObject(String key, byte[] content, String contentType, Map<String, String> metadata) {
        logger.debug("Putting blob: {} size={} metadata={}", key, content.length, metadata);
        BlobClient blobClient = containerClient.getBlobClient(key);
        
        BlobHttpHeaders headers = new BlobHttpHeaders().setContentType(contentType);
        BlobParallelUploadOptions options = new BlobParallelUploadOptions(BinaryData.fromBytes(content));
        options.setHeaders(headers);
        options.setMetadata(metadata);
        
        blobClient.uploadWithResponse(options, null, null);
    }
    
    @Override
    public boolean objectExists(String key) {
        try {
            BlobClient blobClient = containerClient.getBlobClient(key);
            return blobClient.exists();
        } catch (BlobStorageException e) {
            if (e.getErrorCode() == BlobErrorCode.BLOB_NOT_FOUND) {
                return false;
            }
            throw e;
        }
    }
    
    @Override
    public Map<String, String> getObjectMetadata(String key) {
        BlobClient blobClient = containerClient.getBlobClient(key);
        BlobProperties properties = blobClient.getProperties();
        return properties.getMetadata();
    }
    
    @Override
    public long getObjectSize(String key) {
        BlobClient blobClient = containerClient.getBlobClient(key);
        BlobProperties properties = blobClient.getProperties();
        return properties.getBlobSize();
    }
    
    @Override
    public void deleteObject(String key) {
        logger.debug("Deleting blob: {}", key);
        BlobClient blobClient = containerClient.getBlobClient(key);
        blobClient.deleteIfExists();
    }
    
    @Override
    public List<String> listObjects(String prefix) {
        logger.debug("Listing blobs with prefix: {}", prefix);
        ListBlobsOptions options = new ListBlobsOptions().setPrefix(prefix);
        
        return StreamSupport.stream(
            containerClient.listBlobs(options, null).spliterator(), 
            false
        )
        .map(BlobItem::getName)
        .collect(Collectors.toList());
    }
    
    @Override
    public ListObjectsResult listObjects(String prefix, int maxKeys, String startAfter) {
        logger.debug("Listing blobs with prefix: {} maxKeys={} startAfter={}", prefix, maxKeys, startAfter);
        
        ListBlobsOptions options = new ListBlobsOptions()
                .setPrefix(prefix)
                .setMaxResultsPerPage(maxKeys);
        
        // Get the first page
        Iterable<PagedResponse<BlobItem>> pages = containerClient.listBlobs(options, null).iterableByPage();
        PagedResponse<BlobItem> firstPage = pages.iterator().next();
        
        List<String> keys = StreamSupport.stream(firstPage.getValue().spliterator(), false)
                .map(BlobItem::getName)
                .map(name -> name.substring(prefix.length())) // Remove prefix
                .collect(Collectors.toList());
        
        // Check if there are more pages
        String continuationToken = firstPage.getContinuationToken();
        boolean isTruncated = continuationToken != null;
        String nextMarker = isTruncated && !keys.isEmpty() ? keys.get(keys.size() - 1) : null;
        
        return new ListObjectsResult(keys, nextMarker, isTruncated);
    }
}
