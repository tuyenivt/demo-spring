# WebSocket Subproject

## Overview

Real-time bidirectional communication demo using Spring Boot WebSocket with STOMP protocol. Features broadcast messaging, private messaging, system notifications, message history, and graceful shutdown.

## Tech Stack

- Java 25, Spring Boot 4.1 WebSocket + STOMP
- SockJS fallback for browser compatibility
- Jackson 3 for STOMP payloads (Boot auto-configured `JsonMapper`; DTOs use `com.fasterxml.jackson.annotation` annotations)
- Virtual threads enabled
- Lombok for boilerplate reduction

## Project Structure

```
websocket/
├── src/main/java/com/example/websocket/
│   ├── DemoWebSocketApplication.java         # Entry point
│   ├── config/
│   │   ├── WebSocketConfig.java              # STOMP/WebSocket configuration
│   │   ├── UserAuthChannelInterceptor.java   # Username extraction from headers
│   │   ├── WebSocketEventListener.java       # Connect/disconnect events
│   │   └── WebSocketShutdownNotifier.java    # Shutdown broadcast on ContextClosedEvent
│   ├── constant/
│   │   └── WebSocketDestinations.java        # Destination constants + message strings
│   ├── controller/
│   │   ├── ChatController.java               # STOMP message handlers
│   │   └── AdminController.java              # REST admin endpoints (no auth)
│   ├── dto/
│   │   ├── ChatMessage.java                  # Input DTO (record + @JsonCreator)
│   │   ├── ChatResponse.java                 # Output DTO (record + @JsonFormat)
│   │   └── ErrorResponse.java               # Error DTO (record)
│   ├── exception/
│   │   └── WebSocketErrorHandler.java        # @ControllerAdvice + @MessageExceptionHandler
│   ├── handler/
│   │   └── MessageBroadcastHandler.java      # Broadcasting logic
│   └── service/
│       └── MessageHistoryService.java        # In-memory history (ArrayDeque, max 50)
├── src/main/resources/
│   ├── application.yml
│   └── static/                              # Test client UI
│       ├── index.html
│       ├── css/styles.css
│       └── js/script.js
└── build.gradle
```

## Key Components

### Configuration

| Class                        | Purpose                                                                                                        |
|------------------------------|----------------------------------------------------------------------------------------------------------------|
| `WebSocketConfig`            | STOMP broker setup, endpoint registration, transport limits; declares `brokerTaskScheduler` bean for heartbeat |
| `UserAuthChannelInterceptor` | Extract username from CONNECT headers; auto-generates `user-{timestamp}` if missing                            |
| `WebSocketEventListener`     | Broadcast join/leave notifications on session events                                                           |
| `WebSocketShutdownNotifier`  | `@EventListener(ContextClosedEvent)` — broadcasts shutdown message before the broker/sessions are stopped      |

### STOMP Destinations

| Destination            | Type     | Purpose                                      |
|------------------------|----------|----------------------------------------------|
| `/ws`                  | Endpoint | WebSocket connection entry (SockJS fallback) |
| `/app/chat.send`       | App      | Send broadcast message                       |
| `/app/chat.private`    | App      | Send private message                         |
| `/app/history`         | App      | Subscribe to receive last 50 messages        |
| `/topic/messages`      | Broker   | Receive all broadcasts                       |
| `/topic/notifications` | Broker   | System notifications (join/leave/shutdown)   |
| `/user/queue/private`  | User     | Receive private messages                     |
| `/user/queue/errors`   | User     | Receive error responses                      |

### Controllers

- **ChatController**: `@MessageMapping` handlers
  - `handleChatMessage()` — broadcast to all; stores username in session attributes
  - `handlePrivateMessage()` — send to specific user; validates no self-messaging
  - `handleNotificationSubscription()` — `@SubscribeMapping("/notifications")` returns welcome notification directly to the subscriber (not broadcast to topic)
  - `handleHistorySubscription()` — returns last 50 messages on `/app/history` subscribe

- **AdminController**: REST API (`/api/admin`)
  - `POST /api/admin/notify` — broadcast system notification (no authentication — demo only)

### DTOs (Java Records)

```java
// Input — @JsonCreator with @JsonProperty for explicit Jackson deserialization
record ChatMessage(String username, String content, String targetUsername)

// Output — @JsonFormat for ISO-8601 timestamp
record ChatResponse(String username, String content, Instant timestamp, String messageType)

// Error
record ErrorResponse(String errorCode, String message, Instant timestamp)
```

### Message History

`MessageHistoryService` stores the last 50 broadcast messages in an `ArrayDeque` protected by `ReentrantLock`. Late-joining clients retrieve history via `@SubscribeMapping("/history")`.

### Exception Handling

`WebSocketErrorHandler` (`@ControllerAdvice`) catches:
- `IllegalArgumentException` → `VALIDATION_ERROR` code
- Generic `Exception` → `MESSAGE_PROCESSING_ERROR` code

Both use `@SendToUser` to route errors to `/user/queue/errors` (per-user delivery).

## Message Flow

### Broadcast Message
```
Client → /app/chat.send → ChatController → MessageBroadcastHandler
       → /topic/messages (all clients)
       → /topic/notifications ("New message from X")
       → MessageHistoryService (stored)
```

### Private Message
```
Client → /app/chat.private → ChatController → MessageBroadcastHandler
       → convertAndSendToUser() → /user/{target}/queue/private → Target client
```

### System Notification
```
Event/Admin/Shutdown → MessageBroadcastHandler.broadcastSystemNotification()
                     → /topic/notifications → All clients
```

## Transport Hardening (WebSocketConfig)

| Setting            | Value                  |
|--------------------|------------------------|
| STOMP heartbeat    | 10s send / 10s receive |
| SockJS heartbeat   | 25s                    |
| Message size limit | 8 KB                   |
| Send buffer size   | 512 KB                 |
| Send time limit    | 15s                    |

## Running

```bash
./gradlew websocket:bootRun
```

Access test client at http://localhost:8080

## Configuration

```yaml
server:
  port: 8080
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 15s
  threads:
    virtual:
      enabled: true
  jackson:
    deserialization:
      fail-on-unknown-properties: false
```

Jackson 3 writes `java.time` values as ISO-8601 strings by default; `ChatResponse`/`ErrorResponse` also pin the format with `@JsonFormat`.

## Important Notes

- **Authentication**: Demo uses header-based username (not secure for production); missing username auto-generates `user-{timestamp}`
- **Message Broker**: In-memory simple broker (single instance only)
- **Validation**: Max message length 1000 chars; no self-messaging in private chat
- **Admin API**: `/api/admin/notify` has no authentication — demo only
- **XSS Protection**: Frontend escapes HTML in messages

## Testing

Integration tests use `WebSocketStompClient` + `SockJsClient` (with `JacksonJsonMessageConverter`) over a random port:
- `WebSocketIntegrationTests` (`@SpringBootTest(RANDOM_PORT)`) — broadcast, private message, validation error, join notification; sessions disconnected after each test
- `ShutdownNotificationTests` — starts the app with `SpringApplication.run`, subscribes to `/topic/notifications`, closes the context and expects the shutdown notification

Run with:

```bash
./gradlew websocket:test
```
