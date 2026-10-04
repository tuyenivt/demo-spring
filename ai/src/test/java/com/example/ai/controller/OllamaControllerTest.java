package com.example.ai.controller;

import com.example.ai.exception.AiExceptionHandler;
import com.example.ai.exception.AiServiceException;
import com.example.ai.service.OllamaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({OllamaController.class, AiExceptionHandler.class})
class OllamaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OllamaService service;

    @Test
    void question_shouldReturnAnswer() throws Exception {
        when(service.getAnswer("user1", "Hello")).thenReturn("Hi there");

        mockMvc.perform(post("/question/user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "Hello"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Hi there"))
                .andExpect(jsonPath("$.conversationId").value("user1"));
    }

    @Test
    void question_shouldReturnBadRequestForBlankQuestion() throws Exception {
        mockMvc.perform(post("/question/user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": ""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("question: Question cannot be blank"));
    }

    @Test
    void question_shouldReturnBadRequestForOversizedQuestion() throws Exception {
        var longQuestion = "a".repeat(2001);
        mockMvc.perform(post("/question/user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"" + longQuestion + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void question_shouldReturnBadRequestForOversizedUserId() throws Exception {
        var longUserId = "a".repeat(101);
        mockMvc.perform(post("/question/" + longUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "Hello"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void question_shouldReturn503OnAiServiceFailure() throws Exception {
        when(service.getAnswer(anyString(), anyString())).thenThrow(new AiServiceException("AI unavailable"));

        mockMvc.perform(post("/question/user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "Hello"}"""))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void streamQuestion_shouldReturnEventStream() throws Exception {
        when(service.streamAnswer("user1", "Hello")).thenReturn(Flux.just("chunk1", "chunk2"));

        mockMvc.perform(get("/question/user1/stream")
                        .param("question", "Hello"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    void streamQuestion_shouldReturnBadRequestForBlankQuestion() throws Exception {
        mockMvc.perform(get("/question/user1/stream")
                        .param("question", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void streamQuestion_shouldReturnBadRequestForOversizedQuestion() throws Exception {
        var longQuestion = "a".repeat(2001);
        mockMvc.perform(get("/question/user1/stream")
                        .param("question", longQuestion))
                .andExpect(status().isBadRequest());
    }
}
