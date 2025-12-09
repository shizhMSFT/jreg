package com.jreg;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract tests for OCI Referrers API
 * Tests GET /v2/{name}/referrers/{digest} with artifactType filtering
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ReferrersContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testReferrersEmpty() throws Exception {
        String repository = "referrersempty";
        String nonExistentDigest = "sha256:1111111111111111111111111111111111111111111111111111111111111111";
        
        // Query referrers for non-existent digest
        mockMvc.perform(get("/v2/{name}/referrers/{digest}", repository, nonExistentDigest))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.oci.image.index.v1+json"))
                .andExpect(jsonPath("$.schemaVersion").value(2))
                .andExpect(jsonPath("$.mediaType").value("application/vnd.oci.image.index.v1+json"))
                .andExpect(jsonPath("$.manifests").isArray())
                .andExpect(jsonPath("$.manifests").isEmpty());
    }

    @Test
    public void testReferrersWithSubject() throws Exception {
        String repository = "referrerswithsubject";
        
        // Push base manifest (subject)
        String subjectManifest = """
            {
              "schemaVersion": 2,
              "mediaType": "application/vnd.oci.image.manifest.v1+json",
              "config": {
                "mediaType": "application/vnd.oci.image.config.v1+json",
                "digest": "sha256:44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a",
                "size": 2
              },
              "layers": [
                {
                  "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
                  "digest": "sha256:6b86b273ff34fce19d6b804eff5a3f5747ada4eaa22f1d49c01e52ddb7875b4b",
                  "size": 1
                }
              ]
            }
            """;
        
        String digestHeader = mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, "base")
                .contentType("application/vnd.oci.image.manifest.v1+json")
                .content(subjectManifest))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Docker-Content-Digest");
        
        // Push referrer manifest (signature)
        String referrerManifest = """
            {
              "schemaVersion": 2,
              "mediaType": "application/vnd.oci.image.manifest.v1+json",
              "artifactType": "application/vnd.example.signature.v1",
              "config": {
                "mediaType": "application/vnd.oci.empty.v1+json",
                "digest": "sha256:44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a",
                "size": 2
              },
              "layers": [
                {
                  "mediaType": "application/octet-stream",
                  "digest": "sha256:d4735e3a265e16eee03f59718b9b5d03019c07d8b6c51f90da3a666eec13ab35",
                  "size": 1
                }
              ],
              "subject": {
                "mediaType": "application/vnd.oci.image.manifest.v1+json",
                "digest": "%s",
                "size": 527
              }
            }
            """.formatted(digestHeader);
        
        mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, "signature")
                .contentType("application/vnd.oci.image.manifest.v1+json")
                .content(referrerManifest))
                .andExpect(status().isCreated());
        
        // Query referrers
        mockMvc.perform(get("/v2/{name}/referrers/{digest}", repository, digestHeader))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.oci.image.index.v1+json"))
                .andExpect(jsonPath("$.schemaVersion").value(2))
                .andExpect(jsonPath("$.mediaType").value("application/vnd.oci.image.index.v1+json"))
                .andExpect(jsonPath("$.manifests").isArray())
                .andExpect(jsonPath("$.manifests", hasSize(1)))
                .andExpect(jsonPath("$.manifests[0].mediaType").value("application/vnd.oci.image.manifest.v1+json"))
                .andExpect(jsonPath("$.manifests[0].artifactType").value("application/vnd.example.signature.v1"));
    }

    @Test
    public void testReferrersMultiple() throws Exception {
        String repository = "referrersmultiple";
        
        // Push base manifest
        String subjectManifest = """
            {
              "schemaVersion": 2,
              "mediaType": "application/vnd.oci.image.manifest.v1+json",
              "config": {
                "mediaType": "application/vnd.oci.image.config.v1+json",
                "digest": "sha256:44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a",
                "size": 2
              },
              "layers": [
                {
                  "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
                  "digest": "sha256:6b86b273ff34fce19d6b804eff5a3f5747ada4eaa22f1d49c01e52ddb7875b4b",
                  "size": 1
                }
              ]
            }
            """;
        
        String subjectDigest = mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, "base")
                .contentType("application/vnd.oci.image.manifest.v1+json")
                .content(subjectManifest))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Docker-Content-Digest");
        
        // Push signature referrer
        String signatureManifest = """
            {
              "schemaVersion": 2,
              "mediaType": "application/vnd.oci.image.manifest.v1+json",
              "artifactType": "application/vnd.example.signature.v1",
              "config": {
                "mediaType": "application/vnd.oci.empty.v1+json",
                "digest": "sha256:44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a",
                "size": 2
              },
              "layers": [
                {
                  "mediaType": "application/octet-stream",
                  "digest": "sha256:d4735e3a265e16eee03f59718b9b5d03019c07d8b6c51f90da3a666eec13ab35",
                  "size": 1
                }
              ],
              "subject": {
                "mediaType": "application/vnd.oci.image.manifest.v1+json",
                "digest": "%s",
                "size": 527
              }
            }
            """.formatted(subjectDigest);
        
        mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, "sig1")
                .contentType("application/vnd.oci.image.manifest.v1+json")
                .content(signatureManifest))
                .andExpect(status().isCreated());
        
        // Push SBOM referrer
        String sbomManifest = """
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
                  "mediaType": "application/octet-stream",
                  "digest": "sha256:4e07408562bedb8b60ce05c1decfe3ad16b72230967de01f640b7e4729b49fce",
                  "size": 1
                }
              ],
              "subject": {
                "mediaType": "application/vnd.oci.image.manifest.v1+json",
                "digest": "%s",
                "size": 527
              }
            }
            """.formatted(subjectDigest);
        
        mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, "sbom1")
                .contentType("application/vnd.oci.image.manifest.v1+json")
                .content(sbomManifest))
                .andExpect(status().isCreated());
        
        // Query all referrers
        mockMvc.perform(get("/v2/{name}/referrers/{digest}", repository, subjectDigest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manifests", hasSize(2)))
                .andExpect(jsonPath("$.manifests[*].artifactType", 
                    containsInAnyOrder("application/vnd.example.signature.v1", "application/vnd.example.sbom.v1")));
    }

    @Test
    public void testReferrersFilterByArtifactType() throws Exception {
        String repository = "referrersfilter";
        
        // Push base manifest
        String subjectManifest = """
            {
              "schemaVersion": 2,
              "mediaType": "application/vnd.oci.image.manifest.v1+json",
              "config": {
                "mediaType": "application/vnd.oci.image.config.v1+json",
                "digest": "sha256:44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a",
                "size": 2
              },
              "layers": [
                {
                  "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
                  "digest": "sha256:6b86b273ff34fce19d6b804eff5a3f5747ada4eaa22f1d49c01e52ddb7875b4b",
                  "size": 1
                }
              ]
            }
            """;
        
        String subjectDigest = mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, "base")
                .contentType("application/vnd.oci.image.manifest.v1+json")
                .content(subjectManifest))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Docker-Content-Digest");
        
        // Push signature referrer
        String signatureManifest = """
            {
              "schemaVersion": 2,
              "mediaType": "application/vnd.oci.image.manifest.v1+json",
              "artifactType": "application/vnd.example.signature.v1",
              "config": {
                "mediaType": "application/vnd.oci.empty.v1+json",
                "digest": "sha256:44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a",
                "size": 2
              },
              "layers": [
                {
                  "mediaType": "application/octet-stream",
                  "digest": "sha256:d4735e3a265e16eee03f59718b9b5d03019c07d8b6c51f90da3a666eec13ab35",
                  "size": 1
                }
              ],
              "subject": {
                "mediaType": "application/vnd.oci.image.manifest.v1+json",
                "digest": "%s",
                "size": 527
              }
            }
            """.formatted(subjectDigest);
        
        mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, "sig1")
                .contentType("application/vnd.oci.image.manifest.v1+json")
                .content(signatureManifest))
                .andExpect(status().isCreated());
        
        // Push SBOM referrer
        String sbomManifest = """
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
                  "mediaType": "application/octet-stream",
                  "digest": "sha256:4e07408562bedb8b60ce05c1decfe3ad16b72230967de01f640b7e4729b49fce",
                  "size": 1
                }
              ],
              "subject": {
                "mediaType": "application/vnd.oci.image.manifest.v1+json",
                "digest": "%s",
                "size": 527
              }
            }
            """.formatted(subjectDigest);
        
        mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, "sbom1")
                .contentType("application/vnd.oci.image.manifest.v1+json")
                .content(sbomManifest))
                .andExpect(status().isCreated());
        
        // Query only signature referrers
        mockMvc.perform(get("/v2/{name}/referrers/{digest}", repository, subjectDigest)
                .param("artifactType", "application/vnd.example.signature.v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manifests", hasSize(1)))
                .andExpect(jsonPath("$.manifests[0].artifactType").value("application/vnd.example.signature.v1"));
    }

    @Test
    public void testReferrersInvalidDigest() throws Exception {
        String repository = "referrersinvalid";
        String invalidDigest = "not-a-digest";
        
        // Query with invalid digest
        mockMvc.perform(get("/v2/{name}/referrers/{digest}", repository, invalidDigest))
                .andExpect(status().isBadRequest());
    }
}
