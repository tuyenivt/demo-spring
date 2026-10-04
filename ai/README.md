# Spring AI Demo

## Overview
This is a demo AI-powered customer support chatbot designed to assist users with inquiries about **HealthConnect**, a telehealth platform.

## Features
- Integration with **Ollama**, utilizing the **LLaMA 3.1** language model
- Externalised system prompt (`prompts/system-prompt.st`) — editable without recompiling
- Persistent **ChatMemory** to enable contextual, multi-turn conversations
- **Qdrant** used as a vector store for semantic search and retrieval
- Retrieval-Augmented Generation (RAG) powered by **QuestionAnswerAdvisor** for more accurate and context-aware responses
- Idempotent RAG document loading — avoids duplicates on restart
- **Request/Response DTOs** with input validation on all endpoints
- **Streaming responses** via Server-Sent Events (SSE)
- **Conversation history** endpoint to retrieve past messages
- **Document management** endpoint for adding RAG documents
- **Rate limiting** with Resilience4j
- **Health checks** for Ollama service via Spring Actuator
- **Observability** with Micrometer metrics and structured logging
- **Integration tests** with Testcontainers
- **Unit tests** with `@WebMvcTest` and Mockito for fast feedback

## Start Ollama
```bash
docker run -d --name ai-ollama -p 11434:11434 -v ollama:/root/.ollama ollama/ollama:0.35.1
```
Environment Variable:
- `OLLAMA_BASE_URL=http://localhost:11434`
- `OLLAMA_MODEL=llama3.1`

## Start Vector DB
```bash
docker run -d --name ai-qdrant -p 6333:6333 -p 6334:6334 qdrant/qdrant:v1.19
```
Environment Variable:
- `QDRANT_HOST=localhost`
- `QDRANT_PORT=6334`

## Test via REST API

### Ask a Question
```bash
curl -X POST http://localhost:8080/question/test_user \
     -H "Content-Type: application/json" \
     -d '{"question": "What are insurance supported?"}'
```

### Stream Response (SSE)
```bash
curl -N http://localhost:8080/question/test_user/stream?question=What%20insurance%20providers%20are%20supported
```

### Get Conversation History
```bash
curl http://localhost:8080/conversations/test_user
```

### Get Conversation History with Limit
```bash
curl http://localhost:8080/conversations/test_user?limit=10
```

### Clear Conversation
```bash
curl -X DELETE http://localhost:8080/conversations/test_user
```

### Add Document to Vector Store
```bash
curl -X POST http://localhost:8080/admin/documents \
     -H "Content-Type: application/json" \
     -d '{"content": "New policy information here", "metadata": {"source": "admin"}}'
```

### Delete Document
```bash
curl -X DELETE http://localhost:8080/admin/documents/{id}
```

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

### Metrics
```bash
curl http://localhost:8080/actuator/metrics
```

### AI-specific Metrics
```bash
curl http://localhost:8080/actuator/metrics/ai.questions.total
curl http://localhost:8080/actuator/metrics/ai.response.time
```

## Test Chat Memory via REST API
```bash
curl -X POST http://localhost:8080/question/test_user \
     -H "Content-Type: application/json" \
     -d '{"question": "My name is Spring"}'
```

```bash
curl -X POST http://localhost:8080/question/test_user \
     -H "Content-Type: application/json" \
     -d '{"question": "Do you remember my name?"}'
```
