package com.vaibhav.ediflow.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PhaseOneApiTest {
    @Autowired MockMvc mvc;

    @Test void uploadPersistsAnOrderThatCanBeListedAndRead() throws Exception {
        byte[] edi = Files.readAllBytes(Path.of("src/test/resources/fixtures/valid-850.edi"));
        String response = mvc.perform(multipart("/api/v1/edi/upload").file(new MockMultipartFile("file", "valid-850.edi", "text/plain", edi)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PERSISTED")).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(response, "$.orderId");
        mvc.perform(get("/api/v1/orders")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].poNumber").value("PO-100245"));
        mvc.perform(get("/api/v1/orders/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.lines.length()").value(2)).andExpect(jsonPath("$.totalAmount").value(1687.50));
    }

    @Test void malformedUploadReturnsAUseful422() throws Exception {
        byte[] edi = Files.readAllBytes(Path.of("src/test/resources/fixtures/malformed-850.edi"));
        mvc.perform(multipart("/api/v1/edi/upload").file(new MockMultipartFile("file", "malformed-850.edi", "text/plain", edi)))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("segment count")));
    }
}
