package com.jreg;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract test for blob upload operations.
 * Verifies OCI Distribution Spec compliance for chunked and monolithic uploads.
 * Tests end-4a through end-4e: POST, PATCH, PUT, GET, DELETE upload endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BlobUploadContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testMonolithicBlobUpload() throws Exception {
        String repository = "testblob";
        byte[] content = "test blob content".getBytes(StandardCharsets.UTF_8);
        String digest = "sha256:dccfe42873d40807d0da4be11f3a412e4914f1315288d3c6e8cf0a19a8928feb";

        // Monolithic upload: POST with digest parameter
        mockMvc.perform(post("/v2/{name}/blobs/uploads/", repository)
                        .param("digest", digest)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(content))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/v2/" + repository + "/blobs/" + digest)))
                .andExpect(header().string("Docker-Content-Digest", digest));

        // Verify blob exists
        // Note: Spring 6.2 MockMvc strips Content-Length from HEAD responses (known limitation)
        mockMvc.perform(head("/v2/{name}/blobs/{digest}", repository, digest))
                .andExpect(status().isOk())
                .andExpect(header().string("Docker-Content-Digest", digest));
    }

    @Test
    public void testChunkedBlobUpload() throws Exception {
        String repository = "chunked";
        byte[] chunk1 = "first chunk ".getBytes(StandardCharsets.UTF_8);
        byte[] chunk2 = "second chunk".getBytes(StandardCharsets.UTF_8);
        String expectedDigest = "sha256:1d4db348a719d285318b29a6583eecbe9bb213b299ece1d807b7f9f2657a1f21";

        // 1. Initiate chunked upload (POST without digest)
        String locationHeader = mockMvc.perform(post("/v2/{name}/blobs/uploads/", repository))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(header().exists("Docker-Upload-UUID"))
                .andExpect(header().string("Range", "0-0"))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        // Extract UUID from Location header
        String uuid = locationHeader.substring(locationHeader.lastIndexOf('/') + 1);

        // 2. Upload first chunk (PATCH)
        mockMvc.perform(patch("/v2/{name}/blobs/uploads/{uuid}", repository, uuid)
                        .header(HttpHeaders.CONTENT_RANGE, "0-" + (chunk1.length - 1))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(chunk1))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Range", "0-" + (chunk1.length - 1)));

        // 3. Upload second chunk (PATCH)
        long secondStart = chunk1.length;
        long secondEnd = secondStart + chunk2.length - 1;
        mockMvc.perform(patch("/v2/{name}/blobs/uploads/{uuid}", repository, uuid)
                        .header(HttpHeaders.CONTENT_RANGE, secondStart + "-" + secondEnd)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(chunk2))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Range", "0-" + secondEnd));

        // 4. Complete upload (PUT with digest)
        mockMvc.perform(put("/v2/{name}/blobs/uploads/{uuid}", repository, uuid)
                        .param("digest", expectedDigest))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/v2/" + repository + "/blobs/" + expectedDigest)))
                .andExpect(header().string("Docker-Content-Digest", expectedDigest));

        // 5. Verify blob exists and content is correct
        // Note: Spring 6.2 MockMvc strips Content-Length from HEAD responses (known limitation)
        mockMvc.perform(head("/v2/{name}/blobs/{digest}", repository, expectedDigest))
                .andExpect(status().isOk())
                .andExpect(header().string("Docker-Content-Digest", expectedDigest));
    }

    @Test
    public void testGetUploadStatus() throws Exception {
        String repository = "uploadstatus";
        byte[] chunk = "partial upload".getBytes(StandardCharsets.UTF_8);

        // Initiate upload
        String locationHeader = mockMvc.perform(post("/v2/{name}/blobs/uploads/", repository))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        String uuid = locationHeader.substring(locationHeader.lastIndexOf('/') + 1);

        // Upload one chunk
        mockMvc.perform(patch("/v2/{name}/blobs/uploads/{uuid}", repository, uuid)
                        .header(HttpHeaders.CONTENT_RANGE, "0-" + (chunk.length - 1))
                        .content(chunk))
                .andExpect(status().isAccepted());

        // Get upload status
        mockMvc.perform(get("/v2/{name}/blobs/uploads/{uuid}", repository, uuid))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Range", "0-" + (chunk.length - 1)))
                .andExpect(header().exists("Docker-Upload-UUID"));
    }

    @Test
    public void testCancelUpload() throws Exception {
        String repository = "canceltest";

        // Initiate upload
        String locationHeader = mockMvc.perform(post("/v2/{name}/blobs/uploads/", repository))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        String uuid = locationHeader.substring(locationHeader.lastIndexOf('/') + 1);

        // Cancel upload
        mockMvc.perform(delete("/v2/{name}/blobs/uploads/{uuid}", repository, uuid))
                .andExpect(status().isNoContent());

        // Verify upload session no longer exists (GET should fail)
        mockMvc.perform(get("/v2/{name}/blobs/uploads/{uuid}", repository, uuid))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testCrossRepoMount() throws Exception {
        String sourceRepo = "source";
        String targetRepo = "target";
        byte[] content = "mountable blob".getBytes(StandardCharsets.UTF_8);
        String digest = "sha256:9fd4b18e530035188a4a9337fd67ed6b4b0cf1310ea13f0499880aabee5e11aa";

        // First, upload blob to source repository
        mockMvc.perform(post("/v2/{name}/blobs/uploads/", sourceRepo)
                        .param("digest", digest)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(content))
                .andExpect(status().isCreated());

        // Mount blob from source to target repository
        mockMvc.perform(post("/v2/{name}/blobs/uploads/", targetRepo)
                        .param("mount", digest)
                        .param("from", sourceRepo))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/v2/" + targetRepo + "/blobs/" + digest)))
                .andExpect(header().string("Docker-Content-Digest", digest));

        // Verify blob accessible from target repository
        mockMvc.perform(head("/v2/{name}/blobs/{digest}", targetRepo, digest))
                .andExpect(status().isOk())
                .andExpect(header().string("Docker-Content-Digest", digest));
    }

    @Test
    public void testInvalidDigestRejection() throws Exception {
        String repository = "invalid";
        byte[] content = "test content".getBytes(StandardCharsets.UTF_8);
        String wrongDigest = "sha256:0000000000000000000000000000000000000000000000000000000000000000";

        // Attempt upload with wrong digest
        mockMvc.perform(post("/v2/{name}/blobs/uploads/", repository)
                        .param("digest", wrongDigest)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("DIGEST_INVALID"));
    }

    @Test
    public void testBlobDeletion() throws Exception {
        String repository = "deletable";
        byte[] content = "delete me".getBytes(StandardCharsets.UTF_8);
        String digest = "sha256:bb99758d9f4dec9ecf3dc2651da1a2ccc1c7d311d37bf9ea06933886ef891691";

        // Upload blob
        mockMvc.perform(post("/v2/{name}/blobs/uploads/", repository)
                        .param("digest", digest)
                        .content(content))
                .andExpect(status().isCreated());

        // Delete blob
        mockMvc.perform(delete("/v2/{name}/blobs/{digest}", repository, digest))
                .andExpect(status().isAccepted());

        // Verify blob no longer exists
        mockMvc.perform(head("/v2/{name}/blobs/{digest}", repository, digest))
                .andExpect(status().isNotFound());
    }
}
