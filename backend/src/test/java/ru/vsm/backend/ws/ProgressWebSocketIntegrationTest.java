package ru.vsm.backend.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.scenario.domain.UserProgress;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import ru.vsm.backend.scenario.repository.UserProgressRepository;
import ru.vsm.backend.scenario.web.ScenarioPlayController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Сквозной тест {@code ru.vsm.backend.ws} поверх реального HTTP-сервера (WebSocket handshake
 * недоступен через {@code MockMvc}, поэтому {@code webEnvironment = RANDOM_PORT}): живой таймер
 * (tick/timeout) и рассылка {@code state} на REST-выбор, сделанный параллельно тем же игроком.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class ProgressWebSocketIntegrationTest {

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
    private UserProgressRepository userProgressRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /** Собирает все входящие текстовые сообщения сессии в порядке получения. */
    private static class RecordingHandler extends TextWebSocketHandler {
        final List<String> messages = new CopyOnWriteArrayList<>();
        volatile CountDownLatch latch = new CountDownLatch(0);

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
            latch.countDown();
        }

        void awaitAtLeast(int count, Duration timeout) throws InterruptedException {
            latch = new CountDownLatch(Math.max(0, count - messages.size()));
            latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    @Test
    void tickAndServerAppliedTimeoutArriveOverWebSocketWhenDeadlinePasses() throws Exception {
        UUID scenarioId = scenarioRepository.findByCode("medical-passenger-unwell").orElseThrow().getId();
        UUID playerId = UUID.randomUUID();

        String startResponse = mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID progressId = UUID.fromString(objectMapper.readTree(startResponse).get("progressId").asText());

        // Сокращаем реальное ожидание: сдвигаем дедлайн уже засеянного 30-секундного таймера
        // на 2 секунды вперёд вместо ожидания полных 30с (тот же приём, что и в REST-тестах
        // сервиса — манипуляция дедлайном напрямую в БД, без sleep на секундах датасета).
        UserProgress progress = userProgressRepository.findById(progressId).orElseThrow();
        progress.setNodeDeadlineAt(Instant.now().plusSeconds(2));
        userProgressRepository.save(progress);

        RecordingHandler handler = new RecordingHandler();
        WebSocketSession session = connect(handler, progressId, playerId);
        try {
            handler.awaitAtLeast(1, Duration.ofSeconds(2));
            JsonNode initial = objectMapper.readTree(handler.messages.get(0));
            assertThat(initial.get("type").asText()).isEqualTo("state");
            assertThat(initial.get("status").asText()).isEqualTo("IN_PROGRESS");

            await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                    assertThat(handler.messages.stream().anyMatch(m -> m.contains("\"type\":\"tick\""))).isTrue());

            await().atMost(Duration.ofSeconds(6)).untilAsserted(() ->
                    assertThat(handler.messages.stream().anyMatch(m -> m.contains("\"type\":\"timeout\""))).isTrue());

            JsonNode timeoutMsg = handler.messages.stream()
                    .map(objectMapper::readTree)
                    .filter(m -> "timeout".equals(m.get("type").asText()))
                    .findFirst().orElseThrow();
            assertThat(timeoutMsg.get("appliedChoiceCode").asText()).isEqualTo("freeze-and-wait");
            assertThat(timeoutMsg.get("status").asText()).isEqualTo("COMPLETED");
            assertThat(timeoutMsg.get("finalOutcome").asText()).isEqualTo("FAILURE");

            assertThat(handler.messages.stream().anyMatch(m -> m.contains("\"type\":\"completed\""))).isTrue();
        } finally {
            session.close(CloseStatus.NORMAL);
        }

        UserProgress completed = userProgressRepository.findById(progressId).orElseThrow();
        assertThat(completed.getStatus().name()).isEqualTo("COMPLETED");
    }

    @Test
    void restChoiceWhileWebSocketConnectedPushesStateEvent() throws Exception {
        UUID scenarioId = scenarioRepository.findByCode("boarding-no-ticket").orElseThrow().getId();
        UUID playerId = UUID.randomUUID();

        String startResponse = mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode startBody = objectMapper.readTree(startResponse);
        UUID progressId = UUID.fromString(startBody.get("progressId").asText());
        UUID offerToCheckChoiceId = findChoiceId(startBody.get("currentNode").get("choices"), "offer-to-check");

        RecordingHandler handler = new RecordingHandler();
        WebSocketSession session = connect(handler, progressId, playerId);
        try {
            handler.awaitAtLeast(1, Duration.ofSeconds(2)); // начальный snapshot

            mockMvc.perform(post(
                            "/api/scenarios/progress/{progressId}/choices/{choiceId}", progressId, offerToCheckChoiceId)
                            .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                    .andExpect(status().isOk());

            await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                    assertThat(handler.messages.stream().anyMatch(m -> m.contains("\"type\":\"state\"")
                            && m.contains("check-app"))).isTrue());

            JsonNode stateMsg = handler.messages.stream()
                    .map(objectMapper::readTree)
                    .filter(m -> "state".equals(m.get("type").asText())
                            && m.has("currentNode") && "check-app".equals(m.get("currentNode").get("code").asText()))
                    .findFirst().orElseThrow();
            assertThat(stateMsg.get("loyaltyScore").asInt()).isEqualTo(3);
            assertThat(stateMsg.get("safetyScore").asInt()).isEqualTo(2);
            assertThat(stateMsg.get("status").asText()).isEqualTo("IN_PROGRESS");
        } finally {
            session.close(CloseStatus.NORMAL);
        }
    }

    private WebSocketSession connect(WebSocketHandler handler, UUID progressId, UUID playerId) throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = URI.create("ws://localhost:" + port + "/ws/progress/" + progressId + "?playerId=" + playerId);
        return client.execute(handler, uri.toString()).get(5, TimeUnit.SECONDS);
    }

    private UUID findChoiceId(JsonNode choices, String code) {
        for (JsonNode choice : choices) {
            if (code.equals(choice.get("code").asText())) {
                return UUID.fromString(choice.get("id").asText());
            }
        }
        throw new IllegalStateException("Выбор '" + code + "' не найден среди " + choices);
    }
}
