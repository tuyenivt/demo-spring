package com.example.ai.controller;

import com.example.ai.exception.AiExceptionHandler;
import com.example.ai.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({DocumentController.class, AiExceptionHandler.class})
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @Test
    void addDocument_shouldReturnCreatedWithId() throws Exception {
        when(documentService.addDocument(anyString(), any())).thenReturn("doc-123");

        mockMvc.perform(post("/admin/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "Test document", "metadata": {"source": "test"}}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("doc-123"))
                .andExpect(jsonPath("$.status").value("added"));
    }

    @Test
    void addDocument_shouldReturnBadRequestForBlankContent() throws Exception {
        mockMvc.perform(post("/admin/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "", "metadata": {}}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addDocument_shouldReturnBadRequestForOversizedContent() throws Exception {
        var longContent = "a".repeat(10001);
        mockMvc.perform(post("/admin/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"" + longContent + "\", \"metadata\": {}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteDocument_shouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/admin/documents/doc-123"))
                .andExpect(status().isNoContent());

        verify(documentService).delete("doc-123");
    }
}
