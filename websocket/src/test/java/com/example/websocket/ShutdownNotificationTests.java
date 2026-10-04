package com.example.websocket;

import com.example.websocket.dto.ChatResponse;
import com.example.websocket.handler.MessageBroadcastHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.example.websocket.constant.WebSocketDestinations.MSG_SERVER_SHUTTING_DOWN;
import static com.example.websocket.constant.WebSocketDestinations.TOPIC_NOTIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the application in its own context so the test can close it and observe the shutdown broadcast.
 */
class ShutdownNotificationTests {

    @Test
    void shouldNotifyConnectedClientsWhenApplicationShutsDown() throws Exception {
        var context = SpringApplication.run(DemoWebSocketApplication.class, "--server.port=0");
        var port = ((WebServerApplicationContext) context).getWebServer().getPort();

        var stompClient = new WebSocketStompClient(new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        try {
            var connectHeaders = new StompHeaders();
            connectHeaders.add("username", "listener");
            var session = stompClient.connectAsync("http://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                    connectHeaders, new StompSessionHandlerAdapter() {
                    }).get(5, TimeUnit.SECONDS);

            BlockingQueue<String> notifications = new LinkedBlockingQueue<>();
            session.subscribe(TOPIC_NOTIFICATIONS, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return ChatResponse.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    notifications.add(((ChatResponse) payload).content());
                }
            });

            // SUBSCRIBE is asynchronous: broadcast until the subscriber receives a message
            var broadcastHandler = context.getBean(MessageBroadcastHandler.class);
            String received = null;
            for (int attempt = 0; attempt < 50 && !"ready".equals(received); attempt++) {
                broadcastHandler.broadcastSystemNotification("ready");
                received = notifications.poll(100, TimeUnit.MILLISECONDS);
            }
            assertThat(received).as("subscription registered").isEqualTo("ready");

            context.close();

            String notification;
            do {
                notification = notifications.poll(5, TimeUnit.SECONDS);
                assertThat(notification).as("shutdown notification").isNotNull();
            } while (!MSG_SERVER_SHUTTING_DOWN.equals(notification));
        } finally {
            stompClient.stop();
            context.close();
        }
    }
}
