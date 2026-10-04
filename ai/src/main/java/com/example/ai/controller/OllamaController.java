package com.example.ai.controller;

import com.example.ai.dto.AnswerResponse;
import com.example.ai.dto.QuestionRequest;
import com.example.ai.service.OllamaService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/question")
@RequiredArgsConstructor
public class OllamaController {
    private final OllamaService service;

    @PostMapping("/{userId}")
    @RateLimiter(name = "questionApi", fallbackMethod = "rateLimitFallback")
    public AnswerResponse question(
            @PathVariable @Size(min = 1, max = 100) String userId,
            @RequestBody @Valid QuestionRequest request) {
        return new AnswerResponse(service.getAnswer(userId, request.question()), userId);
    }

    @GetMapping(value = "/{userId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimiter(name = "questionApi", fallbackMethod = "streamRateLimitFallback")
    public Flux<String> streamQuestion(
            @PathVariable @Size(min = 1, max = 100) String userId,
            @RequestParam @NotBlank @Size(max = 2000) String question) {
        return service.streamAnswer(userId, question);
    }

    @SuppressWarnings("unused") // Parameters required by Resilience4j fallback signature
    public AnswerResponse rateLimitFallback(String userId, QuestionRequest request, RequestNotPermitted e) {
        return new AnswerResponse("Too many requests. Please try again later.", userId);
    }

    @SuppressWarnings("unused") // Parameters required by Resilience4j fallback signature
    public Flux<String> streamRateLimitFallback(String userId, String question, RequestNotPermitted e) {
        return Flux.just("Too many requests. Please try again later.");
    }
}
