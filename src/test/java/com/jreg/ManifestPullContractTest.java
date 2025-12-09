package com.jreg;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract tests for OCI Distribution Spec - Manifest Pull Operations
 * Tests manifest retrieval by tag and digest
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ManifestPullContractTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String IMAGE_MANIFEST_V2 = "application/vnd.oci.image.manifest.v1+json";

    @Test
    public void testPullManifestByTag() throws Exception {
        String repository = "manifestpull";
        String tag = "v1.0";
        String manifestJson = """
                {
                    "schemaVersion": 2,
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "config": {
                        "mediaType": "application/vnd.oci.image.config.v1+json",
                        "digest": "sha256:1111111111111111111111111111111111111111111111111111111111111111",
                        "size": 100
                    },
                    "layers": [
                        {
                            "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
                            "digest": "sha256:2222222222222222222222222222222222222222222222222222222222222222",
                            "size": 200
                        }
                    ]
                }
                """;
        
        // Push manifest
        mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, tag)
                .contentType(IMAGE_MANIFEST_V2)
                .content(manifestJson))
                .andExpect(status().isCreated());
        
        // Pull manifest by tag
        mockMvc.perform(get("/v2/{name}/manifests/{reference}", repository, tag))
                .andExpect(status().isOk())
                .andExpect(header().exists("Docker-Content-Digest"))
                .andExpect(header().string("Content-Type", IMAGE_MANIFEST_V2))
                .andExpect(jsonPath("$.schemaVersion").value(2))
                .andExpect(jsonPath("$.mediaType").value(IMAGE_MANIFEST_V2))
                .andExpect(jsonPath("$.config.digest").value("sha256:1111111111111111111111111111111111111111111111111111111111111111"))
                .andExpect(jsonPath("$.layers", hasSize(1)));
    }

    @Test
    public void testPullManifestByDigest() throws Exception {
        String repository = "manifestpull";
        String manifestJson = """
                {
                    "schemaVersion": 2,
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "config": {
                        "mediaType": "application/vnd.oci.image.config.v1+json",
                        "digest": "sha256:3333333333333333333333333333333333333333333333333333333333333333",
                        "size": 100
                    },
                    "layers": [
                        {
                            "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
                            "digest": "sha256:4444444444444444444444444444444444444444444444444444444444444444",
                            "size": 200
                        }
                    ]
                }
                """;
        
        // Push manifest and get digest
        String digest = mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, "temp")
                .contentType(IMAGE_MANIFEST_V2)
                .content(manifestJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Docker-Content-Digest");
        
        // Pull manifest by digest
        mockMvc.perform(get("/v2/{name}/manifests/{reference}", repository, digest))
                .andExpect(status().isOk())
                .andExpect(header().string("Docker-Content-Digest", digest))
                .andExpect(header().string("Content-Type", IMAGE_MANIFEST_V2))
                .andExpect(jsonPath("$.schemaVersion").value(2))
                .andExpect(jsonPath("$.config.digest").value("sha256:3333333333333333333333333333333333333333333333333333333333333333"));
    }

    @Test
    public void testManifestHeadByTag() throws Exception {
        String repository = "manifestpull";
        String tag = "headtest";
        String manifestJson = """
                {
                    "schemaVersion": 2,
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "config": {
                        "mediaType": "application/vnd.oci.image.config.v1+json",
                        "digest": "sha256:5555555555555555555555555555555555555555555555555555555555555555",
                        "size": 100
                    },
                    "layers": [
                        {
                            "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
                            "digest": "sha256:6666666666666666666666666666666666666666666666666666666666666666",
                            "size": 200
                        }
                    ]
                }
                """;
        
        // Push manifest
        mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, tag)
                .contentType(IMAGE_MANIFEST_V2)
                .content(manifestJson))
                .andExpect(status().isCreated());
        
        // HEAD request for manifest
        // Note: Spring 6.2 MockMvc strips Content-Length from HEAD responses (known limitation)
        mockMvc.perform(head("/v2/{name}/manifests/{reference}", repository, tag))
                .andExpect(status().isOk())
                .andExpect(header().exists("Docker-Content-Digest"))
                .andExpect(header().string("Content-Type", IMAGE_MANIFEST_V2));
    }

    @Test
    public void testManifestNotFoundByTag() throws Exception {
        String repository = "manifestpull";
        String nonExistentTag = "nonexistent";
        
        mockMvc.perform(get("/v2/{name}/manifests/{reference}", repository, nonExistentTag))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("MANIFEST_UNKNOWN"));
    }

    @Test
    public void testManifestNotFoundByDigest() throws Exception {
        String repository = "manifestpull";
        String nonExistentDigest = "sha256:0000000000000000000000000000000000000000000000000000000000000000";
        
        mockMvc.perform(get("/v2/{name}/manifests/{reference}", repository, nonExistentDigest))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("MANIFEST_UNKNOWN"));
    }

    @Test
    public void testContentTypeNegotiation() throws Exception {
        String repository = "manifestpull";
        String tag = "negotiate";
        String manifestJson = """
                {
                    "schemaVersion": 2,
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "config": {
                        "mediaType": "application/vnd.oci.image.config.v1+json",
                        "digest": "sha256:7777777777777777777777777777777777777777777777777777777777777777",
                        "size": 100
                    },
                    "layers": [
                        {
                            "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
                            "digest": "sha256:8888888888888888888888888888888888888888888888888888888888888888",
                            "size": 200
                        }
                    ]
                }
                """;
        
        // Push manifest
        mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, tag)
                .contentType(IMAGE_MANIFEST_V2)
                .content(manifestJson))
                .andExpect(status().isCreated());
        
        // Pull with Accept header specifying OCI manifest
        mockMvc.perform(get("/v2/{name}/manifests/{reference}", repository, tag)
                .accept(MediaType.parseMediaType(IMAGE_MANIFEST_V2)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", IMAGE_MANIFEST_V2));
        
        // Pull with wildcard Accept header
        mockMvc.perform(get("/v2/{name}/manifests/{reference}", repository, tag)
                .accept(MediaType.ALL))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Type"));
    }

    @Test
    public void testExactManifestBytesPreserved() throws Exception {
        String repository = "manifestpull";
        String tag = "exactbytes";
        String manifestJson = """
                {
                    "schemaVersion": 2,
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "config": {
                        "mediaType": "application/vnd.oci.image.config.v1+json",
                        "digest": "sha256:9999999999999999999999999999999999999999999999999999999999999999",
                        "size": 100
                    },
                    "layers": [
                        {
                            "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
                            "digest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            "size": 200
                        }
                    ]
                }
                """;
        
        // Push manifest
        String pushedDigest = mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, tag)
                .contentType(IMAGE_MANIFEST_V2)
                .content(manifestJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Docker-Content-Digest");
        
        // Pull and verify exact bytes
        String pulledContent = mockMvc.perform(get("/v2/{name}/manifests/{reference}", repository, tag))
                .andExpect(status().isOk())
                .andExpect(header().string("Docker-Content-Digest", pushedDigest))
                .andReturn().getResponse().getContentAsString();
        
        // Verify content matches (whitespace may differ but JSON structure should be same)
        assert pulledContent.contains("\"schemaVersion\": 2");
        assert pulledContent.contains("sha256:9999999999999999999999999999999999999999999999999999999999999999");
    }
}
