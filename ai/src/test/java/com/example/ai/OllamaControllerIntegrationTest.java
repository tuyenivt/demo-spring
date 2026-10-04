package com.example.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OllamaControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnBadRequestForEmptyQuestion() throws Exception {
        mockMvc.perform(post("/question/test_user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAcceptValidUserId() throws Exception {
        var longUserId = "a".repeat(50);
        mockMvc.perform(post("/question/" + longUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"test\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldGetConversationHistory() throws Exception {
        mockMvc.perform(get("/conversations/test_user"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void shouldClearConversation() throws Exception {
        mockMvc.perform(delete("/conversations/test_user"))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldAddDocument() throws Exception {
        mockMvc.perform(post("/admin/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Test document content\", \"metadata\": {}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("added"));
    }

    @Test
    void healthEndpointShouldBeAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void metricsEndpointShouldBeAccessible() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk());
    }
}
