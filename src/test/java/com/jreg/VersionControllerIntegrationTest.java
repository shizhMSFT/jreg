package com.jreg;

import com.jreg.config.TestS3Config;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestS3Config.class)
class VersionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testGetApiVersion() throws Exception {
        mockMvc.perform(get("/v2/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Docker-Distribution-API-Version", "registry/2.0"))
                .andExpect(jsonPath("$.version").value("1.0.0"));
    }
}
