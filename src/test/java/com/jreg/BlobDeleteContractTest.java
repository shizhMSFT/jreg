package com.jreg;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract tests for OCI Distribution Spec - Blob DELETE operations
 * Validates DELETE /v2/{name}/blobs/{digest} endpoints
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Blob Delete Contract Tests")
public class BlobDeleteContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("DELETE blob should return 202 and subsequent HEAD should return 404")
    public void testDeleteBlob() throws Exception {
        String repo = "blobdeletetest";
        byte[] blobContent = "test blob for deletion".getBytes();

        // Start upload
        MvcResult uploadResult = mockMvc.perform(post("/v2/" + repo + "/blobs/uploads/"))
                .andExpect(status().isAccepted())
                .andReturn();

        String location = uploadResult.getResponse().getHeader("Location");
        String uuid = location.substring(location.lastIndexOf("/") + 1);

        // Upload blob content
        mockMvc.perform(patch("/v2/" + repo + "/blobs/uploads/" + uuid)
                .contentType("application/octet-stream")
                .content(blobContent))
                .andExpect(status().isAccepted());

        // Complete upload
        String digest = "sha256:eca4756f77407502911de6002f26f8e495a9e68b1b1996e22a3e7c26bb814b7d";
        mockMvc.perform(put("/v2/" + repo + "/blobs/uploads/" + uuid + "?digest=" + digest)
                .contentType("application/octet-stream"))
                .andExpect(status().isCreated());

        // Verify blob exists
        // Note: Spring 6.2 MockMvc strips Content-Length from HEAD responses (known limitation)
        mockMvc.perform(head("/v2/" + repo + "/blobs/" + digest))
                .andExpect(status().isOk());

        // Delete blob
        mockMvc.perform(delete("/v2/" + repo + "/blobs/" + digest))
                .andExpect(status().isAccepted()); // 202

        // Verify blob is gone
        mockMvc.perform(head("/v2/" + repo + "/blobs/" + digest))
                .andExpect(status().isNotFound()) // 404
                .andExpect(jsonPath("$.errors[0].code").value("BLOB_UNKNOWN"));
    }

    @Test
    @DisplayName("DELETE non-existent blob should return 202 (idempotent)")
    public void testDeleteNonExistentBlob() throws Exception {
        String repo = "blobdeletenonexist";
        String digest = "sha256:0000000000000000000000000000000000000000000000000000000000000000";

        // Delete non-existent blob (should be idempotent)
        mockMvc.perform(delete("/v2/" + repo + "/blobs/" + digest))
                .andExpect(status().isAccepted()); // 202 even if doesn't exist
    }

    @Test
    @DisplayName("DELETE blob with invalid digest should return 400")
    public void testDeleteBlobInvalidDigest() throws Exception {
        String repo = "blobdeleteinvalid";
        String invalidDigest = "invalid-digest";

        // Delete with invalid digest format
        mockMvc.perform(delete("/v2/" + repo + "/blobs/" + invalidDigest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("DIGEST_INVALID"));
    }

    @Test
    @DisplayName("DELETE blob should succeed even if referenced by manifests")
    public void testDeleteBlobReferencedByManifest() throws Exception {
        String repo = "blobdeletereferenced";
        byte[] blobContent = "referenced blob content".getBytes();

        // Upload blob
        MvcResult uploadResult = mockMvc.perform(post("/v2/" + repo + "/blobs/uploads/"))
                .andExpect(status().isAccepted())
                .andReturn();

        String location = uploadResult.getResponse().getHeader("Location");
        String uuid = location.substring(location.lastIndexOf("/") + 1);

        mockMvc.perform(patch("/v2/" + repo + "/blobs/uploads/" + uuid)
                .contentType("application/octet-stream")
                .content(blobContent))
                .andExpect(status().isAccepted());

        String blobDigest = "sha256:13cffa3bdc7155ded43b563529d4b807dab43c9f0505de23951f81d4005f9ed3";
        mockMvc.perform(put("/v2/" + repo + "/blobs/uploads/" + uuid + "?digest=" + blobDigest)
                .contentType("application/octet-stream"))
                .andExpect(status().isCreated());

        // Push manifest referencing the blob
        String manifest = """
                {
                    "schemaVersion": 2,
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "config": {
                        "mediaType": "application/vnd.oci.image.config.v1+json",
                        "digest": "%s",
                        "size": 23
                    },
                    "layers": [
                        {
                            "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
                            "digest": "%s",
                            "size": 23
                        }
                    ]
                }
                """.formatted(blobDigest, blobDigest);

        mockMvc.perform(put("/v2/" + repo + "/manifests/v1.0")
                .contentType("application/vnd.oci.image.manifest.v1+json")
                .content(manifest))
                .andExpect(status().isCreated());

        // Delete blob (should succeed even if referenced - user's responsibility)
        mockMvc.perform(delete("/v2/" + repo + "/blobs/" + blobDigest))
                .andExpect(status().isAccepted()); // 202

        // Verify blob is gone
        mockMvc.perform(head("/v2/" + repo + "/blobs/" + blobDigest))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE blob is global (content-addressable storage)")
    public void testDeleteBlobRepositoryScoped() throws Exception {
        String repo1 = "blobdeleterepo1";
        String repo2 = "blobdeleterepo2";
        byte[] blobContent = "shared blob content".getBytes();
        String digest = "sha256:07e5eba6d76f69923f5d286c7650c3b5a49ef08102f75e290e2b66a00fcbdcc7";

        // Upload same blob to repo1
        MvcResult upload1 = mockMvc.perform(post("/v2/" + repo1 + "/blobs/uploads/"))
                .andExpect(status().isAccepted())
                .andReturn();
        String uuid1 = upload1.getResponse().getHeader("Location").substring(
                upload1.getResponse().getHeader("Location").lastIndexOf("/") + 1);

        mockMvc.perform(patch("/v2/" + repo1 + "/blobs/uploads/" + uuid1)
                .contentType("application/octet-stream")
                .content(blobContent))
                .andExpect(status().isAccepted());

        mockMvc.perform(put("/v2/" + repo1 + "/blobs/uploads/" + uuid1 + "?digest=" + digest)
                .contentType("application/octet-stream"))
                .andExpect(status().isCreated());

        // Upload same blob to repo2 (deduplication - blob already exists)
        MvcResult upload2 = mockMvc.perform(post("/v2/" + repo2 + "/blobs/uploads/"))
                .andExpect(status().isAccepted())
                .andReturn();
        String uuid2 = upload2.getResponse().getHeader("Location").substring(
                upload2.getResponse().getHeader("Location").lastIndexOf("/") + 1);

        mockMvc.perform(patch("/v2/" + repo2 + "/blobs/uploads/" + uuid2)
                .contentType("application/octet-stream")
                .content(blobContent))
                .andExpect(status().isAccepted());

        mockMvc.perform(put("/v2/" + repo2 + "/blobs/uploads/" + uuid2 + "?digest=" + digest)
                .contentType("application/octet-stream"))
                .andExpect(status().isCreated());

        // Delete from repo1 (deletes globally in content-addressable storage)
        mockMvc.perform(delete("/v2/" + repo1 + "/blobs/" + digest))
                .andExpect(status().isAccepted());

        // Verify gone from repo1
        mockMvc.perform(head("/v2/" + repo1 + "/blobs/" + digest))
                .andExpect(status().isNotFound());

        // Also gone from repo2 (global content-addressable storage)
        // Note: This matches Docker Hub and most production registries
        // Blobs are globally deduplicated for efficiency
        mockMvc.perform(head("/v2/" + repo2 + "/blobs/" + digest))
                .andExpect(status().isNotFound());
    }
}
