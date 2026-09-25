package ru.vsm.backend.scenario.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.domain.ScenarioChoiceHistory;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.domain.UserProgress;
import ru.vsm.backend.scenario.event.ScenarioCompletedEvent;
import ru.vsm.backend.scenario.repository.ScenarioChoiceHistoryRepository;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import ru.vsm.backend.scenario.repository.UserProgressRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Сквозной тест REST API прохождения (см. {@code ScenarioPlayController}/{@code ScenarioPlayService}):
 * полное прохождение сценария 01 до терминального узла (дельты шкал, идентификация игрока
 * заголовком {@code X-Player-Id}, публикация {@link ScenarioCompletedEvent}), плюс серверная
 * проверка таймера (явный timeout-запрос и выбор, пришедший после дедлайна) на сценарии 19,
 * у которого entry-узел с таймером.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@RecordApplicationEvents
class ScenarioPlayApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScenarioRepository scenarioRepository;

    @Autowired
    private UserProgressRepository userProgressRepository;

    @Autowired
    private ScenarioChoiceHistoryRepository scenarioChoiceHistoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullPlaythroughToTerminalNodeAppliesScalesAndPublishesEvent(ApplicationEvents events) throws Exception {
        UUID scenarioId = scenarioRepository.findByCode("boarding-no-ticket").orElseThrow().getId();
        UUID playerId = UUID.randomUUID();

        String startResponse = mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.loyaltyScore").value(0))
                .andExpect(jsonPath("$.safetyScore").value(0))
                .andExpect(jsonPath("$.currentNode.code").value("start"))
                .andExpect(jsonPath("$.currentNode.choices").isArray())
                .andReturn().getResponse().getContentAsString();

        JsonNode startBody = objectMapper.readTree(startResponse);
        UUID progressId = UUID.fromString(startBody.get("progressId").asText());
        UUID offerToCheckChoiceId = findChoiceId(startBody.get("currentNode").get("choices"), "offer-to-check");

        String afterFirstChoice = mockMvc.perform(post(
                        "/api/scenarios/progress/{progressId}/choices/{choiceId}", progressId, offerToCheckChoiceId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wasTimeout").value(false))
                .andExpect(jsonPath("$.loyaltyDelta").value(3))
                .andExpect(jsonPath("$.safetyDelta").value(2))
                .andExpect(jsonPath("$.loyaltyScore").value(3))
                .andExpect(jsonPath("$.safetyScore").value(2))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.nextNode.code").value("check-app"))
                .andReturn().getResponse().getContentAsString();

        JsonNode afterFirst = objectMapper.readTree(afterFirstChoice);
        UUID foundInSystemChoiceId = findChoiceId(afterFirst.get("nextNode").get("choices"), "found-in-system");

        mockMvc.perform(post(
                        "/api/scenarios/progress/{progressId}/choices/{choiceId}", progressId, foundInSystemChoiceId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wasTimeout").value(false))
                .andExpect(jsonPath("$.loyaltyDelta").value(15))
                .andExpect(jsonPath("$.safetyDelta").value(10))
                .andExpect(jsonPath("$.loyaltyScore").value(18))
                .andExpect(jsonPath("$.safetyScore").value(12))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.finalOutcome").value("SUCCESS"))
                .andExpect(jsonPath("$.nextNode.code").value("resolved-found"))
                .andExpect(jsonPath("$.nextNode.terminal").value(true))
                .andExpect(jsonPath("$.nextNode.terminalOutcome").value("SUCCESS"))
                .andExpect(jsonPath("$.nextNode.choices").isEmpty());

        UserProgress progress = userProgressRepository.findById(progressId).orElseThrow();
        assertThat(progress.getStatus()).isEqualTo(ProgressStatus.COMPLETED);
        assertThat(progress.getCurrentNodeId()).isNull();
        assertThat(progress.getNodeDeadlineAt()).isNull();
        assertThat(progress.getFinalOutcome()).isEqualTo(ScenarioOutcome.SUCCESS);
        assertThat(progress.getLoyaltyScore()).isEqualTo(18);
        assertThat(progress.getSafetyScore()).isEqualTo(12);

        assertThat(scenarioChoiceHistoryRepository.findByUserProgressIdOrderBySequenceIndex(progressId))
                .hasSize(2)
                .extracting(ScenarioChoiceHistory::isWasTimeout)
                .containsExactly(false, false);

        // Завершённое прохождение: currentNode отсутствует, шкалы сохранены.
        mockMvc.perform(get("/api/scenarios/progress/{progressId}", progressId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.currentNode").value(nullValue()))
                .andExpect(jsonPath("$.loyaltyScore").value(18))
                .andExpect(jsonPath("$.safetyScore").value(12));

        // Чужой playerId не может читать чужое прохождение.
        mockMvc.perform(get("/api/scenarios/progress/{progressId}", progressId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        var completedEvents = events.stream(ScenarioCompletedEvent.class).toList();
        assertThat(completedEvents).hasSize(1);
        ScenarioCompletedEvent event = completedEvents.get(0);
        assertThat(event.userProgressId()).isEqualTo(progressId);
        assertThat(event.userId()).isEqualTo(playerId);
        assertThat(event.scenarioId()).isEqualTo(scenarioId);
        assertThat(event.scenarioCode()).isEqualTo("boarding-no-ticket");
        assertThat(event.scenarioBlock()).isEqualTo("boarding");
        assertThat(event.outcome()).isEqualTo(ScenarioOutcome.SUCCESS);
        assertThat(event.loyaltyScore()).isEqualTo(18);
        assertThat(event.safetyScore()).isEqualTo(12);
        assertThat(event.choicesMade()).isEqualTo(2);
        assertThat(event.hadTimeout()).isFalse();
        // offer-to-check {acknowledge,solution} + found-in-system {acknowledge,rule,solution,reassure}
        // объединение покрывает все 4 шага ролевой модели.
        assertThat(event.allRoleStepsFollowed()).isTrue();
        assertThat(event.startedAt()).isNotNull();
        assertThat(event.completedAt()).isNotNull();
    }

    @Test
    void explicitTimeoutRequestAppliesDefaultChoiceAndMarksWasTimeout(ApplicationEvents events) throws Exception {
        UUID scenarioId = scenarioRepository.findByCode("medical-passenger-unwell").orElseThrow().getId();
        UUID playerId = UUID.randomUUID();

        String startResponse = mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentNode.code").value("start"))
                .andExpect(jsonPath("$.currentNode.timerSeconds").value(30))
                .andExpect(jsonPath("$.currentNode.deadlineAt").exists())
                .andReturn().getResponse().getContentAsString();

        UUID progressId = UUID.fromString(objectMapper.readTree(startResponse).get("progressId").asText());

        mockMvc.perform(post("/api/scenarios/progress/{progressId}/timeout", progressId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wasTimeout").value(true))
                .andExpect(jsonPath("$.appliedChoiceCode").value("freeze-and-wait"))
                .andExpect(jsonPath("$.loyaltyDelta").value(-15))
                .andExpect(jsonPath("$.safetyDelta").value(-20))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.finalOutcome").value("FAILURE"))
                .andExpect(jsonPath("$.nextNode.code").value("failure-panic-delay"));

        assertThat(scenarioChoiceHistoryRepository.findByUserProgressIdOrderBySequenceIndex(progressId))
                .singleElement()
                .extracting(ScenarioChoiceHistory::isWasTimeout)
                .isEqualTo(true);

        ScenarioCompletedEvent event = events.stream(ScenarioCompletedEvent.class).findFirst().orElseThrow();
        assertThat(event.hadTimeout()).isTrue();
        assertThat(event.outcome()).isEqualTo(ScenarioOutcome.FAILURE);
        assertThat(event.allRoleStepsFollowed()).isFalse();
    }

    @Test
    void choiceRequestAfterDeadlinePassedIsOverriddenByDefaultChoice() throws Exception {
        UUID scenarioId = scenarioRepository.findByCode("medical-passenger-unwell").orElseThrow().getId();
        UUID playerId = UUID.randomUUID();

        String startResponse = mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andReturn().getResponse().getContentAsString();
        JsonNode startBody = objectMapper.readTree(startResponse);
        UUID progressId = UUID.fromString(startBody.get("progressId").asText());
        UUID nonDefaultChoiceId = findChoiceId(startBody.get("currentNode").get("choices"), "calm-and-call-chief");

        // Симулируем истечение серверного дедлайна напрямую в БД (без sleep в тесте).
        UserProgress progress = userProgressRepository.findById(progressId).orElseThrow();
        progress.setNodeDeadlineAt(Instant.now().minusSeconds(5));
        userProgressRepository.save(progress);

        mockMvc.perform(post(
                        "/api/scenarios/progress/{progressId}/choices/{choiceId}", progressId, nonDefaultChoiceId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wasTimeout").value(true))
                .andExpect(jsonPath("$.appliedChoiceCode").value("freeze-and-wait"))
                .andExpect(jsonPath("$.nextNode.code").value("failure-panic-delay"));
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
