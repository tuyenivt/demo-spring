package com.example.ai.controller;

import com.example.ai.dto.MessageDto;
import com.example.ai.exception.AiExceptionHandler;
import com.example.ai.service.OllamaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({ConversationController.class, AiExceptionHandler.class})
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OllamaService service;

    @Test
    void getConversationHistory_shouldReturnMessages() throws Exception {
        when(service.getConversationHistory("user1", 50))
                .thenReturn(List.of(new MessageDto("USER", "Hello"), new MessageDto("ASSISTANT", "Hi")));

        mockMvc.perform(get("/conversations/user1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].type").value("USER"))
                .andExpect(jsonPath("$[0].content").value("Hello"))
                .andExpect(jsonPath("$[1].type").value("ASSISTANT"));
    }

    @Test
    void getConversationHistory_shouldRespectLimitParam() throws Exception {
        when(service.getConversationHistory("user1", 10)).thenReturn(List.of());

        mockMvc.perform(get("/conversations/user1").param("limit", "10"))
                .andExpect(status().isOk());

        verify(service).getConversationHistory("user1", 10);
    }

    @Test
    void getConversationHistory_shouldReturnBadRequestForInvalidLimit() throws Exception {
        mockMvc.perform(get("/conversations/user1").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("limit: must be greater than or equal to 1"));
    }

    @Test
    void getConversationHistory_shouldReturnBadRequestForOversizedUserId() throws Exception {
        var longUserId = "a".repeat(101);
        mockMvc.perform(get("/conversations/" + longUserId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clearConversation_shouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/conversations/user1"))
                .andExpect(status().isNoContent());

        verify(service).clearConversation("user1");
    }
}
