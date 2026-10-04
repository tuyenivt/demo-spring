# Spring WebSocket Demo

## Run project

```bash
./gradlew websocket:clean websocket:bootRun
```

## Run tests

```bash
./gradlew websocket:test
```

## Access the application

- Open browser to: `http://localhost:8080`
- Enter a username
- Click "Connect"
- Open multiple browser tabs/windows to test broadcasting

## Test functionality

- Broadcast: Type message and click "Send" → All connected clients receive it
- Private: Select a user in the "Online Users" panel, then send a message → Only target user receives it
- Validation: Try sending empty messages or very long messages to see error handling
- History: On connect, last 50 broadcast messages are loaded from `/app/history`
- Reconnect: Unexpected disconnect triggers exponential backoff reconnect (1s → 2s → 4s ... max 30s)
- Shutdown: Server broadcasts shutdown notification before graceful termination

## Runtime hardening added

- STOMP heartbeat enabled: 10s send / 10s receive
- SockJS heartbeat configured: 25s
- Transport message size limit: 8 KB
- Transport send buffer size limit: 512 KB
- Transport send time limit: 15s
- Graceful shutdown enabled: `server.shutdown=graceful`
- Shutdown phase timeout: `spring.lifecycle.timeout-per-shutdown-phase=15s`

## Integration test coverage

- Broadcast routing (`/app/chat.send` → `/topic/messages`)
- Private routing (`/app/chat.private` → `/user/queue/private`)
- Validation error path (`/user/queue/errors`)
- Join notification (`/topic/notifications`)
- Shutdown notification on context close (`ShutdownNotificationTests`, runs the app in its own context)
