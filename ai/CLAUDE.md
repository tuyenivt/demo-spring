# AI Subproject

## Overview
Spring AI-powered customer support chatbot for HealthConnect telehealth platform. Uses Ollama (LLaMA 3.1) with RAG pattern and Qdrant vector store.

## Tech Stack
- Spring Boot + Spring AI 1.1.2
- Ollama (LLaMA 3.1 model)
- Qdrant vector database
- Resilience4j (rate limiting)
- Spring Actuator (health checks, metrics)
- Micrometer (observability)
- Testcontainers (integration tests)
- Lombok

## Project Structure
```
ai/
├── src/main/java/com/example/ai/
│   ├── MainApplication.java
│   ├── config/
│   │   └── AiConfiguration.java         # ChatClient bean + system prompt + advisors
│   ├── controller/
│   │   ├── OllamaController.java        # Question endpoints (POST + SSE stream)
│   │   ├── ConversationController.java  # Conversation history (GET + DELETE)
│   │   └── DocumentController.java      # Document management (POST + DELETE)
│   ├── dto/
│   │   ├── QuestionRequest.java         # Input DTO with @NotBlank @Size(max=2000)
│   │   ├── AnswerResponse.java          # Response DTO (answer + conversationId)
│   │   ├── MessageDto.java              # Conversation message (messageType + text)
│   │   ├── DocumentRequest.java         # Document input (content + metadata map)
│   │   └── ErrorResponse.java           # Error response
│   ├── exception/
│   │   ├── AiExceptionHandler.java      # Global exception handler
│   │   └── AiServiceException.java      # Custom exception → HTTP 503
│   ├── health/
│   │   └── AiHealthIndicator.java       # Ollama health check via OllamaApi.listModels()
│   └── service/
│       ├── OllamaService.java           # Chat logic + metrics
│       ├── RagDocumentLoader.java       # ApplicationRunner; idempotent RAG doc loading
│       └── DocumentService.java         # Document CRUD via VectorStore
├── src/main/resources/
│   ├── application.yml
│   ├── prompts/
│   │   └── system-prompt.st             # Externalised system prompt (StringTemplate)
│   └── docs/                            # RAG documents auto-loaded on startup
│       ├── insurance-policy.txt
│       └── platform-usage.txt
├── src/test/java/com/example/ai/
│   ├── OllamaControllerIntegrationTest.java  # @SpringBootTest + Testcontainers (7 tests)
│   ├── TestcontainersConfiguration.java
│   ├── controller/
│   │   ├── OllamaControllerTest.java         # @WebMvcTest (7 tests)
│   │   ├── ConversationControllerTest.java   # @WebMvcTest
│   │   └── DocumentControllerTest.java       # @WebMvcTest
│   └── service/
│       ├── OllamaServiceTest.java            # Unit test (mocked)
│       └── DocumentServiceTest.java          # Unit test (mocked)
└── build.gradle
```

## Key Components

### AiConfiguration
- Defines `ChatClient` bean with RAG and memory advisors
- Loads system prompt from `classpath:prompts/system-prompt.st`

### OllamaController
- `POST /question/{userId}` - accepts question JSON, returns AI response
- `GET /question/{userId}/stream` - SSE streaming response (`text/event-stream`)
- Rate limiting via Resilience4j (`questionApi`) with fallback responses (returns 200 with message)
- `@Validated`: `@Size(min=1, max=100)` on `userId`; `@NotBlank @Size(max=2000)` on stream `question` param

### ConversationController
- `GET /conversations/{userId}` - get conversation history; `limit` param: `@Min(1) @Max(100)`, default 50
- `DELETE /conversations/{userId}` - clear conversation (returns 204 No Content)

### DocumentController
- `POST /admin/documents` - add documents to vector store (returns 201 Created)
- `DELETE /admin/documents/{id}` - delete document (returns 204 No Content)

### OllamaService
- Uses injected `ChatClient` for AI conversations
- Structured logging at DEBUG (question/answer length) and WARN (failures)
- Micrometer metrics: `ai.questions.total`, `ai.response.time`

### RagDocumentLoader
- `ApplicationRunner` that loads RAG documents on startup
- Idempotent: checks if documents with matching `source` metadata already exist before adding
- Logs document loading results and failures

## Configuration
```yaml
spring.ai.ollama:
  base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
  init:
    pull-model-strategy: always
    timeout: 60s
    max-retries: 1
  chat.options:
    model: ${OLLAMA_MODEL:llama3.1}
    temperature: 0.7

spring.ai.vectorstore.qdrant:
  host: ${QDRANT_HOST:localhost}
  port: ${QDRANT_PORT:6334}
  collection-name: telehealth_docs
  initialize-schema: true

resilience4j.ratelimiter.instances.questionApi:
  limit-for-period: 10
  limit-refresh-period: 1m
  timeout-duration: 0s   # fail-fast, never queue

management.endpoints.web.exposure.include: health,metrics,prometheus
```

## Running Locally

### Prerequisites
```bash
# Ollama
docker run -d --name ai-ollama -p 11434:11434 -v ollama:/root/.ollama ollama/ollama:0.15.2

# Qdrant
docker run -d --name ai-qdrant -p 6333:6333 -p 6334:6334 qdrant/qdrant:v1.16
```

### Test API
```bash
# Ask question
curl -X POST http://localhost:8080/question/test_user \
     -H "Content-Type: application/json" \
     -d '{"question": "What insurance providers are supported?"}'

# Stream response
curl -N http://localhost:8080/question/test_user/stream?question=Hello

# Get conversation history
curl http://localhost:8080/conversations/test_user

# Clear conversation
curl -X DELETE http://localhost:8080/conversations/test_user

# Health check
curl http://localhost:8080/actuator/health
```

## Running Tests
```bash
# All tests (integration tests require Docker)
./gradlew :ai:test

# Unit tests only (no Docker required)
./gradlew :ai:test --tests "com.example.ai.service.*" --tests "com.example.ai.controller.*Test"
```

## Spring AI Patterns Used
1. **ChatClient Builder** - fluent API for AI conversations, configured as a Spring bean
2. **Advisors** - `QuestionAnswerAdvisor` (RAG, from `spring-ai-advisors-vector-store`) + `PromptChatMemoryAdvisor` (memory)
3. **VectorStore** - semantic document search with Qdrant
4. **ChatMemory** - per-user conversation tracking via `ChatMemory.CONVERSATION_ID` advisor param
5. **Streaming** - `Flux<String>` SSE via `.stream().content()`
6. **Prompt Template** - system prompt externalized to `.st` resource file, loaded via `@Value Resource`

## Key Implementation Notes
- `AiConfiguration` wires both advisors as `defaultAdvisors` on the `ChatClient` bean
- `RagDocumentLoader` idempotency: checks `vectorStore.similaritySearch` with `filterExpression("source == '...'")` before loading
- `OllamaService.getAnswer()` wraps response in `responseTimer.record()` for Micrometer timing
- `AiHealthIndicator` uses `OllamaApi.listModels()` — reports `modelsAvailable` count in health details
- Rate limiter `timeout-duration: 0s` means requests are immediately rejected (no waiting) when limit exceeded
- `OllamaControllerIntegrationTest` uses `@SpringBootTest + @AutoConfigureMockMvc + @Import(TestcontainersConfiguration)` — requires Docker
