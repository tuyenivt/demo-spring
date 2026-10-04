package com.example.websocket.config;

import com.example.websocket.handler.MessageBroadcastHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import static com.example.websocket.constant.WebSocketDestinations.MSG_SERVER_SHUTTING_DOWN;

/**
 * Tells connected clients the server is going away.
 * <p>
 * Runs on {@link ContextClosedEvent}, which is published before lifecycle beans (web server, STOMP broker,
 * WebSocket sessions) are stopped; a {@code @PreDestroy} callback would run after the broker is gone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketShutdownNotifier {

    private final MessageBroadcastHandler broadcastHandler;

    @EventListener(ContextClosedEvent.class)
    public void notifyClients() {
        log.info("Broadcasting shutdown notification to connected clients");
        broadcastHandler.broadcastSystemNotification(MSG_SERVER_SHUTTING_DOWN);
    }
}
