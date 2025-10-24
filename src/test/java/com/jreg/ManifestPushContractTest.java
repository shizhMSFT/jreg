package com.jreg;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract test for manifest push operations.
 * Verifies OCI Distribution Spec compliance for manifest upload, retrieval, and tagging.
 * Tests end-2, end-3, end-7, end-8a, end-9.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ManifestPushContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String IMAGE_MANIFEST_V2 = "application/vnd.oci.image.manifest.v1+json";
    private static final String IMAGE_INDEX_V1 = "application/vnd.oci.image.index.v1+json";

    @Test
    public void testPushManifestByTag() throws Exception {
        String repository = "manifesttest";
        String tag = "v1.0.0";

        String manifestJson = """
                {
                    "schemaVersion": 2,
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "config": {
                        "mediaType": "application/vnd.oci.image.config.v1+json",
                        "digest": "sha256:b5b2b2c507a0944348e0303114d8d93aaaa081732b86451d9bce1f432a537bc7",
                        "size": 7023
                    },
                    "layers": [
                        {
                            "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
                            "digest": "sha256:e692418e3535c2c7de6e7a9e1f5f8c7c7c6c6c5c4c3c2c1c0c0c0c0c0c0c0c0c",
                            "size": 32654
                        }
                    ]
                }
                """;

        // Push manifest by tag
        String digestHeader = mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, tag)
                        .contentType(IMAGE_MANIFEST_V2)
                        .content(manifestJson))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().exists("Docker-Content-Digest"))
                .andExpect(header().string("Docker-Content-Digest", startsWith("sha256:")))
                .andReturn()
                .getResponse()
                .getHeader("Docker-Content-Digest");

        // Verify manifest retrievable by tag
        mockMvc.perform(get("/v2/{name}/manifests/{reference}", repository, tag)
                        .accept(IMAGE_MANIFEST_V2))
                .andExpect(status().isOk())
                .andExpect(content().contentType(IMAGE_MANIFEST_V2))
                .andExpect(header().string("Docker-Content-Digest", digestHeader))
                .andExpect(jsonPath("$.schemaVersion").value(2))
                .andExpect(jsonPath("$.config.digest").value("sha256:b5b2b2c507a0944348e0303114d8d93aaaa081732b86451d9bce1f432a537bc7"));

        // Verify manifest retrievable by digest
        mockMvc.perform(get("/v2/{name}/manifests/{reference}", repository, digestHeader)
                        .accept(IMAGE_MANIFEST_V2))
                .andExpect(status().isOk())
                .andExpect(content().contentType(IMAGE_MANIFEST_V2))
                .andExpect(header().string("Docker-Content-Digest", digestHeader));
    }

    @Test
    public void testPushManifestByDigest() throws Exception {
        String repository = "digesttest";

        String manifestJson = """
                {
                    "schemaVersion": 2,
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "config": {
                        "mediaType": "application/vnd.oci.image.config.v1+json",
                        "digest": "sha256:c5c6c7c8c9c0c1c2c3c4c5c6c7c8c9c0c1c2c3c4c5c6c7c8c9c0c1c2c3c4c5c6",
                        "size": 1234
                    },
                    "layers": [
                        {
                            "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
                            "digest": "sha256:e692418e3535c2c7de6e7a9e1f5f8c7c7c6c6c5c4c3c2c1c0c0c0c0c0c0c0c0c",
                            "size": 32654
                        }
                    ]
                }
                """;

        // Push manifest by digest - the digest will be calculated from content
        String actualDigest = mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, "sha256:c76c0421aba9718cad03cbf126cb09cca67cc3b5b5e511ebbadc7f7b7c858d3e")
                        .contentType(IMAGE_MANIFEST_V2)
                        .content(manifestJson))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Docker-Content-Digest"))
                .andExpect(header().string("Docker-Content-Digest", startsWith("sha256:")))
                .andReturn()
                .getResponse()
                .getHeader("Docker-Content-Digest");
                
        // Verify we can retrieve it by the digest returned
        mockMvc.perform(get("/v2/{name}/manifests/{reference}", repository, actualDigest)
                        .accept(IMAGE_MANIFEST_V2))
                .andExpect(status().isOk())
                .andExpect(header().string("Docker-Content-Digest", actualDigest));
    }

    @Test
    public void testManifestHeadRequest() throws Exception {
        String repository = "headtest";
        String tag = "latest";

        String manifestJson = """
                {
                    "schemaVersion": 2,
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "config": {
                        "mediaType": "application/vnd.oci.image.config.v1+json",
                        "digest": "sha256:d1d2d3d4d5d6d7d8d9d0d1d2d3d4d5d6d7d8d9d0d1d2d3d4d5d6d7d8d9d0d1d2",
                        "size": 5678
                    },
                    "layers": [
                        {
                            "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
                            "digest": "sha256:e692418e3535c2c7de6e7a9e1f5f8c7c7c6c6c5c4c3c2c1c0c0c0c0c0c0c0c0c",
                            "size": 32654
                        }
                    ]
                }
                """;

        // Push manifest
        String digest = mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, tag)
                        .contentType(IMAGE_MANIFEST_V2)
                        .content(manifestJson))
                .andReturn()
                .getResponse()
                .getHeader("Docker-Content-Digest");

        // HEAD request by tag
        // Note: Spring 6.2 MockMvc strips Content-Length from HEAD responses (known limitation)
        mockMvc.perform(head("/v2/{name}/manifests/{reference}", repository, tag))
                .andExpect(status().isOk())
                .andExpect(header().string("Docker-Content-Digest", digest))
                .andExpect(header().string("Content-Type", IMAGE_MANIFEST_V2));

        // HEAD request by digest
        mockMvc.perform(head("/v2/{name}/manifests/{reference}", repository, digest))
                .andExpect(status().isOk())
                .andExpect(header().string("Docker-Content-Digest", digest));
    }

    @Test
    public void testManifestDeletion() throws Exception {
        String repository = "deletetest";
        String tag = "deleteme";

        String manifestJson = """
                {
                    "schemaVersion": 2,
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "config": {
                        "mediaType": "application/vnd.oci.image.config.v1+json",
                        "digest": "sha256:e1e2e3e4e5e6e7e8e9e0e1e2e3e4e5e6e7e8e9e0e1e2e3e4e5e6e7e8e9e0e1e2",
                        "size": 9999
                    },
                    "layers": [
                        {
                            "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
                            "digest": "sha256:e692418e3535c2c7de6e7a9e1f5f8c7c7c6c6c5c4c3c2c1c0c0c0c0c0c0c0c0c",
                            "size": 32654
                        }
                    ]
                }
                """;

        // Push manifest
        mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, tag)
                        .contentType(IMAGE_MANIFEST_V2)
                        .content(manifestJson))
                .andExpect(status().isCreated());

        // Delete by tag
        mockMvc.perform(delete("/v2/{name}/manifests/{reference}", repository, tag))
                .andExpect(status().isAccepted());

        // Verify manifest no longer accessible by tag
        mockMvc.perform(get("/v2/{name}/manifests/{reference}", repository, tag))
                .andExpect(status().isNotFound());

        // But digest reference might still exist if not cleaned up (depends on GC policy)
    }

    @Test
    public void testListTags() throws Exception {
        String repository = "tagstest";

        // Push multiple manifests with different tags
        String manifest1 = createMinimalManifest("sha256:c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1");
        String manifest2 = createMinimalManifest("sha256:c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2");
        String manifest3 = createMinimalManifest("sha256:c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3");

        mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, "v1.0.0")
                .contentType(IMAGE_MANIFEST_V2)
                .content(manifest1)).andExpect(status().isCreated());

        mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, "v1.0.1")
                .contentType(IMAGE_MANIFEST_V2)
                .content(manifest2)).andExpect(status().isCreated());

        mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, "latest")
                .contentType(IMAGE_MANIFEST_V2)
                .content(manifest3)).andExpect(status().isCreated());

        // List all tags
        mockMvc.perform(get("/v2/{name}/tags/list", repository))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(repository))
                .andExpect(jsonPath("$.tags", hasSize(3)))
                .andExpect(jsonPath("$.tags", hasItems("v1.0.0", "v1.0.1", "latest")));
    }

    @Test
    public void testListTagsWithPagination() throws Exception {
        String repository = "paginationtest";

        // Push 5 tags
        for (int i = 1; i <= 5; i++) {
            // Use properly formatted 64-character hex digests
            String configDigest = "sha256:c%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc%dc".formatted(
                    i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i);
            String manifest = createMinimalManifest(configDigest);
            mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, "tag" + i)
                    .contentType(IMAGE_MANIFEST_V2)
                    .content(manifest)).andExpect(status().isCreated());
        }

        // Request first page (n=2)
        String response1 = mockMvc.perform(get("/v2/{name}/tags/list", repository)
                        .param("n", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(2)))
                .andExpect(header().exists("Link"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        @SuppressWarnings("unchecked")
        Map<String, Object> page1 = objectMapper.readValue(response1, Map.class);
        String lastTag = (String) ((java.util.List<?>) page1.get("tags")).get(1);

        // Request second page (last={lastTag})
        mockMvc.perform(get("/v2/{name}/tags/list", repository)
                        .param("n", "2")
                        .param("last", lastTag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(2)))
                .andExpect(jsonPath("$.tags", not(hasItem(lastTag))));
    }

    @Test
    public void testReferrersAPI() throws Exception {
        String repository = "referrerstest";
        String subjectDigest = "sha256:f1f2f3f4f5f6f7f8f9f0f1f2f3f4f5f6f7f8f9f0f1f2f3f4f5f6f7f8f9f0f1f2";

        // Push subject manifest first
        String subjectManifest = createMinimalManifest("sha256:aaabbbcccdddeeefff000111222333444555666777888999aaabbbcccdddeeef");
        mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, subjectDigest)
                .contentType(IMAGE_MANIFEST_V2)
                .content(subjectManifest)).andExpect(status().isCreated());

        // Push referrer manifest (e.g., signature or SBOM)
        String referrerManifest = """
                {
                    "schemaVersion": 2,
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "artifactType": "application/vnd.example.sbom.v1",
                    "config": {
                        "mediaType": "application/vnd.oci.empty.v1+json",
                        "digest": "sha256:44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a",
                        "size": 2
                    },
                    "layers": [
                        {
                            "mediaType": "application/vnd.example.sbom.v1+json",
                            "digest": "sha256:aaabbbcccdddeeefff111222333444555666777888999000aaabbbcccdddeeef",
                            "size": 1234
                        }
                    ],
                    "subject": {
                        "mediaType": "application/vnd.oci.image.manifest.v1+json",
                        "digest": "%s",
                        "size": 1000
                    }
                }
                """.formatted(subjectDigest);

        mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, "sbom-v1")
                .contentType(IMAGE_MANIFEST_V2)
                .content(referrerManifest)).andExpect(status().isCreated());

        // Query referrers API
        mockMvc.perform(get("/v2/{name}/referrers/{digest}", repository, subjectDigest))
                .andExpect(status().isOk())
                .andExpect(content().contentType(IMAGE_INDEX_V1))
                .andExpect(jsonPath("$.schemaVersion").value(2))
                .andExpect(jsonPath("$.mediaType").value(IMAGE_INDEX_V1))
                .andExpect(jsonPath("$.manifests", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.manifests[0].artifactType").value("application/vnd.example.sbom.v1"));
    }

    @Test
    public void testInvalidManifestRejection() throws Exception {
        String repository = "invalidtest";
        String tag = "broken";

        String invalidManifest = """
                {
                    "this": "is not",
                    "a": "valid manifest"
                }
                """;

        // Attempt to push invalid manifest
        mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, tag)
                        .contentType(IMAGE_MANIFEST_V2)
                        .content(invalidManifest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MANIFEST_INVALID"));
    }

    private String createMinimalManifest(String configDigest) {
        return """
                {
                    "schemaVersion": 2,
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "config": {
                        "mediaType": "application/vnd.oci.image.config.v1+json",
                        "digest": "%s",
                        "size": 100
                    },
                    "layers": [
                        {
                            "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
                            "digest": "sha256:e692418e3535c2c7de6e7a9e1f5f8c7c7c6c6c5c4c3c2c1c0c0c0c0c0c0c0c0c",
                            "size": 100
                        }
                    ]
                }
                """.formatted(configDigest);
    }
}
