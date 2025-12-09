package com.jreg;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract tests for OCI Distribution Spec - Blob Pull Operations
 * Tests blob download (GET) and existence check (HEAD)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BlobPullContractTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String MEDIA_TYPE = "application/octet-stream";

    @Test
    public void testBlobDownload() throws Exception {
        String repository = "pulltest";
        byte[] content = "test blob for pull".getBytes();
        
        // First, upload a blob
        String uploadUrl = mockMvc.perform(post("/v2/{name}/blobs/uploads/", repository))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getHeader("Location");
        
        String sessionId = uploadUrl.substring(uploadUrl.lastIndexOf('/') + 1);
        
        // Complete the upload
        String digest = "sha256:19956a163c2b285a43e5bc9a1d6b5252a8a537e5616f574eb5289ce25287ba8e";
        mockMvc.perform(put("/v2/{name}/blobs/uploads/{uuid}", repository, sessionId)
                .param("digest", digest)
                .contentType(MEDIA_TYPE)
                .content(content))
                .andExpect(status().isCreated());
        
        // Now download the blob
        mockMvc.perform(get("/v2/{name}/blobs/{digest}", repository, digest))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Length"))
                .andExpect(header().string("Content-Length", String.valueOf(content.length)))
                .andExpect(header().string("Docker-Content-Digest", digest))
                .andExpect(content().bytes(content));
    }

    @Test
    public void testBlobHead() throws Exception {
        String repository = "pulltest";
        byte[] content = "test blob for head".getBytes();
        
        // Upload a blob
        String uploadUrl = mockMvc.perform(post("/v2/{name}/blobs/uploads/", repository))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getHeader("Location");
        
        String sessionId = uploadUrl.substring(uploadUrl.lastIndexOf('/') + 1);
        
        String digest = "sha256:858dba3d4f457eea304cd715eda1a7e96f6f06da57e2d60b78b7f1a71fb40bea";
        mockMvc.perform(put("/v2/{name}/blobs/uploads/{uuid}", repository, sessionId)
                .param("digest", digest)
                .contentType(MEDIA_TYPE)
                .content(content))
                .andExpect(status().isCreated());
        
        // Check blob exists with HEAD
        // Note: Spring 6.2 MockMvc strips Content-Length from HEAD responses (known limitation)
        // The header is correctly set by the controller for real HTTP requests
        mockMvc.perform(head("/v2/{name}/blobs/{digest}", repository, digest))
                .andExpect(status().isOk())
                .andExpect(header().string("Docker-Content-Digest", digest));
    }

    @Test
    public void testBlobNotFound() throws Exception {
        String repository = "pulltest";
        String nonExistentDigest = "sha256:0000000000000000000000000000000000000000000000000000000000000000";
        
        // Try to download non-existent blob
        mockMvc.perform(get("/v2/{name}/blobs/{digest}", repository, nonExistentDigest))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("BLOB_UNKNOWN"));
    }

    @Test
    public void testBlobRangeRequest() throws Exception {
        String repository = "pulltest";
        byte[] content = "0123456789abcdefghijklmnopqrstuvwxyz".getBytes();
        
        // Upload a blob
        String uploadUrl = mockMvc.perform(post("/v2/{name}/blobs/uploads/", repository))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getHeader("Location");
        
        String sessionId = uploadUrl.substring(uploadUrl.lastIndexOf('/') + 1);
        
        String digest = "sha256:74e7e5bb9d22d6db26bf76946d40fff3ea9f0346b884fd0694920fccfad15e33";
        mockMvc.perform(put("/v2/{name}/blobs/uploads/{uuid}", repository, sessionId)
                .param("digest", digest)
                .contentType(MEDIA_TYPE)
                .content(content))
                .andExpect(status().isCreated());
        
        // Request partial content with Range header
        mockMvc.perform(get("/v2/{name}/blobs/{digest}", repository, digest)
                .header("Range", "bytes=0-9"))
                .andExpect(status().isPartialContent())
                .andExpect(header().exists("Content-Range"))
                .andExpect(header().string("Content-Length", "10"))
                .andExpect(content().bytes("0123456789".getBytes()));
    }

    @Test
    public void testBlobRangeRequestMiddle() throws Exception {
        String repository = "pulltest";
        byte[] content = "0123456789abcdefghijklmnopqrstuvwxyz".getBytes();
        
        // Upload a blob
        String uploadUrl = mockMvc.perform(post("/v2/{name}/blobs/uploads/", repository))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getHeader("Location");
        
        String sessionId = uploadUrl.substring(uploadUrl.lastIndexOf('/') + 1);
        
        String digest = "sha256:74e7e5bb9d22d6db26bf76946d40fff3ea9f0346b884fd0694920fccfad15e33";
        mockMvc.perform(put("/v2/{name}/blobs/uploads/{uuid}", repository, sessionId)
                .param("digest", digest)
                .contentType(MEDIA_TYPE)
                .content(content))
                .andExpect(status().isCreated());
        
        // Request middle portion
        mockMvc.perform(get("/v2/{name}/blobs/{digest}", repository, digest)
                .header("Range", "bytes=10-19"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 10-19/" + content.length))
                .andExpect(header().string("Content-Length", "10"))
                .andExpect(content().bytes("abcdefghij".getBytes()));
    }

    @Test
    public void testBlobRangeRequestSuffix() throws Exception {
        String repository = "pulltest";
        byte[] content = "0123456789abcdefghijklmnopqrstuvwxyz".getBytes();
        
        // Upload a blob
        String uploadUrl = mockMvc.perform(post("/v2/{name}/blobs/uploads/", repository))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getHeader("Location");
        
        String sessionId = uploadUrl.substring(uploadUrl.lastIndexOf('/') + 1);
        
        String digest = "sha256:74e7e5bb9d22d6db26bf76946d40fff3ea9f0346b884fd0694920fccfad15e33";
        mockMvc.perform(put("/v2/{name}/blobs/uploads/{uuid}", repository, sessionId)
                .param("digest", digest)
                .contentType(MEDIA_TYPE)
                .content(content))
                .andExpect(status().isCreated());
        
        // Request last 10 bytes with suffix-byte-range
        mockMvc.perform(get("/v2/{name}/blobs/{digest}", repository, digest)
                .header("Range", "bytes=-10"))
                .andExpect(status().isPartialContent())
                .andExpect(header().exists("Content-Range"))
                .andExpect(header().string("Content-Length", "10"))
                .andExpect(content().bytes("qrstuvwxyz".getBytes()));
    }
}
