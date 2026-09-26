package ru.vsm.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import tools.jackson.databind.ObjectMapper;

/**
 * {@code app.auth.require-token=true} (см. находку CRITICAL в аудите безопасности, п.3 задачи по
 * исправлению): {@code X-Player-Id}/{@code ?playerId=} перестают приниматься как самостоятельное
 * доказательство личности на игровых эндпоинтах (REST) и WebSocket — нужен валидный
 * {@code Authorization: Bearer}/{@code ?token=}, иначе {@code 401}/закрытие соединения.
 * По умолчанию флаг {@code false} (см. {@code SecurityConfig}) — отдельный {@code @SpringBootTest}
 * с собственным контекстом и контейнером, чтобы не влиять на остальные тесты.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.auth.require-token=true")
@AutoConfigureMockMvc
@Testcontainers
class RequireTokenModeIntegrationTest {

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

    private record Account(UUID playerId, String token) {
    }

    private Account registerAndLogin(String login) throws Exception {
        String registerResponse = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","email":"%s@example.com","password":"password123","displayName":"U"}
                                """.formatted(login, login)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID playerId = UUID.fromString(objectMapper.readTree(registerResponse).get("id").asString());

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","password":"password123"}
                                """.formatted(login)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginResponse).get("token").asString();
        return new Account(playerId, token);
    }

    @Test
    void startingScenarioWithOnlyXPlayerIdIsUnauthorized() throws Exception {
        UUID scenarioId = scenarioRepository.findByCode("boarding-no-ticket").orElseThrow().getId();

        mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header("X-Player-Id", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void startingScenarioWithValidTokenIsAllowedAndHeaderIsIgnored() throws Exception {
        Account account = registerAndLogin("require-token-rest-user");
        UUID scenarioId = scenarioRepository.findByCode("boarding-no-ticket").orElseThrow().getId();

        // X-Player-Id claims a different player, but the token wins (identity comes from JWT only).
        String response = mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header("Authorization", "Bearer " + account.token())
                        .header("X-Player-Id", UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID progressId = UUID.fromString(objectMapper.readTree(response).get("progressId").asString());

        mockMvc.perform(get("/api/scenarios/progress/{progressId}", progressId)
                        .header("Authorization", "Bearer " + account.token()))
                .andExpect(status().isOk());
    }

    @Test
    void profileWithOnlyXPlayerIdIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/gamification/profile/{playerId}", UUID.randomUUID())
                        .header("X-Player-Id", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void websocketClosesWithoutTokenEvenWithPlayerIdQueryParam() throws Exception {
        Account account = registerAndLogin("require-token-ws-user");
        UUID scenarioId = scenarioRepository.findByCode("boarding-no-ticket").orElseThrow().getId();
        String startResponse = mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header("Authorization", "Bearer " + account.token()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID progressId = UUID.fromString(objectMapper.readTree(startResponse).get("progressId").asString());

        RecordingHandler handler = new RecordingHandler();
        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = URI.create(
                "ws://localhost:" + port + "/ws/progress/" + progressId + "?playerId=" + account.playerId());
        WebSocketSession session = client.execute(handler, uri.toString()).get(5, TimeUnit.SECONDS);
        try {
            assertThat(handler.closeLatch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(handler.messages).isEmpty();
        } finally {
            if (session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        }
    }

    @Test
    void websocketAcceptsValidTokenQueryParam() throws Exception {
        Account account = registerAndLogin("require-token-ws-user-ok");
        UUID scenarioId = scenarioRepository.findByCode("boarding-no-ticket").orElseThrow().getId();
        String startResponse = mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header("Authorization", "Bearer " + account.token()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID progressId = UUID.fromString(objectMapper.readTree(startResponse).get("progressId").asString());

        RecordingHandler handler = new RecordingHandler();
        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = URI.create("ws://localhost:" + port + "/ws/progress/" + progressId + "?token=" + account.token());
        WebSocketSession session = client.execute(handler, uri.toString()).get(5, TimeUnit.SECONDS);
        try {
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(handler.messages).isNotEmpty());
        } finally {
            session.close(CloseStatus.NORMAL);
        }
    }

    private static class RecordingHandler extends TextWebSocketHandler {
        final List<String> messages = new CopyOnWriteArrayList<>();
        final CountDownLatch closeLatch = new CountDownLatch(1);

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closeLatch.countDown();
        }
    }
}
