package com.example.websocket;

import com.example.websocket.dto.ChatMessage;
import com.example.websocket.dto.ChatResponse;
import com.example.websocket.dto.ErrorResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketIntegrationTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @LocalServerPort
    private int localServerPort;

    private WebSocketStompClient stompClient;

    private final List<StompSession> sessions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        List<Transport> transports = List.of(new WebSocketTransport(new StandardWebSocketClient()));
        this.stompClient = new WebSocketStompClient(new SockJsClient(transports));
        this.stompClient.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void tearDown() {
        sessions.forEach(StompSession::disconnect);
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    @Test
    void shouldBroadcastMessageToTopicSubscribers() throws Exception {
        var session = connect("alice");
        var received = subscribe(session, "/topic/messages", ChatResponse.class);

        session.send("/app/chat.send", new ChatMessage("alice", "hello everyone", null));

        var response = received.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.content()).isEqualTo("hello everyone");
        assertThat(response.messageType()).isEqualTo("broadcast");
    }

    @Test
    void shouldSendPrivateMessageToTargetUserQueue() throws Exception {
        var sender = connect("alice");
        var receiver = connect("bob");
        var privateMessage = subscribe(receiver, "/user/queue/private", ChatResponse.class);

        sender.send("/app/chat.private", new ChatMessage("alice", "private ping", "bob"));

        var response = privateMessage.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.content()).isEqualTo("private ping");
        assertThat(response.messageType()).isEqualTo("private");
    }

    @Test
    void shouldReturnValidationErrorForInvalidMessage() throws Exception {
        var session = connect("alice");
        var errors = subscribe(session, "/user/queue/errors", ErrorResponse.class);

        session.send("/app/chat.send", new ChatMessage("alice", "", null));

        var response = errors.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertThat(response.errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.message()).contains("Message content cannot be empty");
    }

    @Test
    void shouldNotifyTopicSubscribersWhenUserConnects() throws Exception {
        var observer = connect("observer");
        var notifications = subscribe(observer, "/topic/notifications", ChatResponse.class);

        var joinedUser = connect("charlie");
        assertThat(joinedUser.isConnected()).isTrue();

        var response = notifications.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertThat(response.content()).contains("charlie").contains("joined");
        assertThat(response.messageType()).isEqualTo("notification");
    }

    private StompSession connect(String username) throws Exception {
        var connectHeaders = new StompHeaders();
        connectHeaders.add("username", username);
        var session = stompClient.connectAsync(
                "http://localhost:" + localServerPort + "/ws",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleException(StompSession session, StompCommand command, StompHeaders headers,
                                                byte[] payload, Throwable exception) {
                        // Subscription handlers expose assertion failures through futures.
                    }
                }
        ).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private <T> CompletableFuture<T> subscribe(StompSession session, String destination, Class<T> payloadType) {
        var result = new CompletableFuture<T>();

        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return payloadType;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                result.complete(payloadType.cast(payload));
            }
        });

        return result;
    }
}
