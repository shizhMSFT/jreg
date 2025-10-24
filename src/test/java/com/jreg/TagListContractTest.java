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
 * Contract tests for OCI Distribution Spec - Tag Listing Operations
 * Tests GET /v2/{name}/tags/list with pagination support
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TagListContractTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String MANIFEST_CONTENT = """
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

    @Test
    public void testListTagsEmpty() throws Exception {
        String repository = "taglistempty";
        
        // List tags from empty repository
        mockMvc.perform(get("/v2/{name}/tags/list", repository))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(repository))
                .andExpect(jsonPath("$.tags").isArray())
                .andExpect(jsonPath("$.tags").isEmpty());
    }

    @Test
    public void testListTagsMultiple() throws Exception {
        String repository = "taglistmultiple";
        
        // Push manifests with multiple tags
        String[] tags = {"v1.0", "v1.1", "v2.0", "latest"};
        for (String tag : tags) {
            mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, tag)
                    .contentType("application/vnd.oci.image.manifest.v1+json")
                    .content(MANIFEST_CONTENT))
                    .andExpect(status().isCreated());
        }
        
        // List all tags
        mockMvc.perform(get("/v2/{name}/tags/list", repository))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(repository))
                .andExpect(jsonPath("$.tags").isArray())
                .andExpect(jsonPath("$.tags", hasSize(4)))
                .andExpect(jsonPath("$.tags", containsInAnyOrder("v1.0", "v1.1", "v2.0", "latest")));
    }

    @Test
    public void testListTagsLexicalOrder() throws Exception {
        String repository = "taglistlexical";
        
        // Push tags in non-lexical order
        String[] tags = {"zebra", "alpha", "mike", "bravo"};
        for (String tag : tags) {
            mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, tag)
                    .contentType("application/vnd.oci.image.manifest.v1+json")
                    .content(MANIFEST_CONTENT))
                    .andExpect(status().isCreated());
        }
        
        // Verify tags returned in lexical order
        mockMvc.perform(get("/v2/{name}/tags/list", repository))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0]").value("alpha"))
                .andExpect(jsonPath("$.tags[1]").value("bravo"))
                .andExpect(jsonPath("$.tags[2]").value("mike"))
                .andExpect(jsonPath("$.tags[3]").value("zebra"));
    }

    @Test
    public void testListTagsWithLimit() throws Exception {
        String repository = "taglistlimit";
        
        // Push 10 tags
        for (int i = 1; i <= 10; i++) {
            String tag = "v%02d".formatted(i);
            mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, tag)
                    .contentType("application/vnd.oci.image.manifest.v1+json")
                    .content(MANIFEST_CONTENT))
                    .andExpect(status().isCreated());
        }
        
        // Request only 5 tags
        mockMvc.perform(get("/v2/{name}/tags/list", repository)
                .param("n", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(5)))
                .andExpect(jsonPath("$.tags[0]").value("v01"))
                .andExpect(jsonPath("$.tags[4]").value("v05"));
    }

    @Test
    public void testListTagsWithPagination() throws Exception {
        String repository = "taglistpagination";
        
        // Push 10 tags
        for (int i = 1; i <= 10; i++) {
            String tag = "tag%02d".formatted(i);
            mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, tag)
                    .contentType("application/vnd.oci.image.manifest.v1+json")
                    .content(MANIFEST_CONTENT))
                    .andExpect(status().isCreated());
        }
        
        // First page: limit 3
        mockMvc.perform(get("/v2/{name}/tags/list", repository)
                .param("n", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(3)))
                .andExpect(jsonPath("$.tags[0]").value("tag01"))
                .andExpect(jsonPath("$.tags[2]").value("tag03"));
        
        // Second page: limit 3, last=tag03
        mockMvc.perform(get("/v2/{name}/tags/list", repository)
                .param("n", "3")
                .param("last", "tag03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(3)))
                .andExpect(jsonPath("$.tags[0]").value("tag04"))
                .andExpect(jsonPath("$.tags[2]").value("tag06"));
    }

    @Test
    public void testListTagsLastOnly() throws Exception {
        String repository = "taglistlastonly";
        
        // Push 5 tags
        String[] tags = {"a", "b", "c", "d", "e"};
        for (String tag : tags) {
            mockMvc.perform(put("/v2/{name}/manifests/{reference}", repository, tag)
                    .contentType("application/vnd.oci.image.manifest.v1+json")
                    .content(MANIFEST_CONTENT))
                    .andExpect(status().isCreated());
        }
        
        // Request tags after 'b'
        mockMvc.perform(get("/v2/{name}/tags/list", repository)
                .param("last", "b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(3)))
                .andExpect(jsonPath("$.tags[0]").value("c"))
                .andExpect(jsonPath("$.tags[1]").value("d"))
                .andExpect(jsonPath("$.tags[2]").value("e"));
    }
}
