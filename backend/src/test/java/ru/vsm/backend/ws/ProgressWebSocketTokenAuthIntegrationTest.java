package ru.vsm.backend.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code ?token=<jwt>} как альтернатива {@code playerId} на {@code /ws/progress/{progressId}}
 * (см. {@code ProgressWebSocketHandler.extractPlayerId}) — шаг 2 авторизации.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class ProgressWebSocketTokenAuthIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScenarioRepository scenarioRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static class RecordingHandler extends TextWebSocketHandler {
        final List<String> messages = new CopyOnWriteArrayList<>();
        volatile CountDownLatch latch = new CountDownLatch(1);

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
            latch.countDown();
        }
    }

    @Test
    void websocketAcceptsJwtTokenInPlaceOfPlayerIdQueryParam() throws Exception {
        String registerResponse = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"ws-token-user","email":"ws-token-user@example.com","password":"password123","displayName":"WS"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID playerId = UUID.fromString(objectMapper.readTree(registerResponse).get("id").asText());

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"ws-token-user","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginResponse).get("token").asText();

        UUID scenarioId = scenarioRepository.findByCode("boarding-no-ticket").orElseThrow().getId();
        String startResponse = mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header("X-Player-Id", playerId.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID progressId = UUID.fromString(objectMapper.readTree(startResponse).get("progressId").asText());

        RecordingHandler handler = new RecordingHandler();
        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = URI.create("ws://localhost:" + port + "/ws/progress/" + progressId + "?token=" + token);
        WebSocketSession session = client.execute(handler, uri.toString()).get(5, TimeUnit.SECONDS);
        try {
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(handler.messages).isNotEmpty());
            JsonNode initial = objectMapper.readTree(handler.messages.get(0));
            assertThat(initial.get("type").asText()).isEqualTo("state");
            assertThat(initial.get("status").asText()).isEqualTo("IN_PROGRESS");
        } finally {
            session.close(CloseStatus.NORMAL);
        }
    }

    @Test
    void websocketWithInvalidTokenFallsBackToPlayerIdQueryParam() throws Exception {
        UUID scenarioId = scenarioRepository.findByCode("boarding-no-ticket").orElseThrow().getId();
        UUID playerId = UUID.randomUUID();
        String startResponse = mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header("X-Player-Id", playerId.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID progressId = UUID.fromString(objectMapper.readTree(startResponse).get("progressId").asText());

        RecordingHandler handler = new RecordingHandler();
        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = URI.create("ws://localhost:" + port + "/ws/progress/" + progressId
                + "?token=not-a-real-token&playerId=" + playerId);
        WebSocketSession session = client.execute(handler, uri.toString()).get(5, TimeUnit.SECONDS);
        try {
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(handler.messages).isNotEmpty());
            JsonNode initial = objectMapper.readTree(handler.messages.get(0));
            assertThat(initial.get("type").asText()).isEqualTo("state");
        } finally {
            session.close(CloseStatus.NORMAL);
        }
    }
}
