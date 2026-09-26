package ru.vsm.backend.scenario.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Сквозной REST-тест режима экзамена (см. {@code ExamController}/{@code ExamService}):
 * создание экзамена, отсутствие подсказок (дельт/шкал) в ответе выбора внутри экзамена при том,
 * что тот же граф в обычном режиме их раскрывает как прежде, и блокировка разбора прохождения,
 * пока экзамен не завершён.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ExamApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createExamReturnsTenUniqueScenariosFromDistinctBlocks() throws Exception {
        UUID playerId = UUID.randomUUID();

        String body = mockMvc.perform(post("/api/exams").header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scenarios.length()").value(10))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.currentIndex").value(0))
                .andExpect(jsonPath("$.result").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode exam = objectMapper.readTree(body);
        long distinctBlocks = java.util.stream.StreamSupport.stream(exam.get("scenarios").spliterator(), false)
                .map(n -> n.get("block").asText())
                .distinct()
                .count();
        assertThat(distinctBlocks).isEqualTo(10);
    }

    @Test
    void examModeHidesDeltasAndScoresWhileNormalModeStillShowsThem() throws Exception {
        UUID playerId = UUID.randomUUID();

        String examBody = mockMvc.perform(post("/api/exams").header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID examId = UUID.fromString(objectMapper.readTree(examBody).get("examId").asText());

        String startBody = mockMvc.perform(post("/api/exams/{examId}/current", examId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode startNode = objectMapper.readTree(startBody);
        UUID progressId = UUID.fromString(startNode.get("progressId").asText());
        UUID scenarioId = UUID.fromString(startNode.get("scenarioId").asText());
        UUID choiceId = UUID.fromString(startNode.get("currentNode").get("choices").get(0).get("id").asText());

        mockMvc.perform(post("/api/scenarios/progress/{progressId}/choices/{choiceId}", progressId, choiceId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loyaltyDelta").doesNotExist())
                .andExpect(jsonPath("$.safetyDelta").doesNotExist())
                .andExpect(jsonPath("$.loyaltyScore").doesNotExist())
                .andExpect(jsonPath("$.safetyScore").doesNotExist())
                .andExpect(jsonPath("$.status").exists());

        // Тот же самый сценарий, обычное (не экзаменационное) прохождение другим игроком —
        // дельты/шкалы раскрываются как и раньше (регрессия обычного режима).
        UUID normalPlayerId = UUID.randomUUID();
        String normalStart = mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, normalPlayerId.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode normalStartNode = objectMapper.readTree(normalStart);
        UUID normalProgressId = UUID.fromString(normalStartNode.get("progressId").asText());
        UUID normalChoiceId = UUID.fromString(normalStartNode.get("currentNode").get("choices").get(0).get("id").asText());

        mockMvc.perform(post("/api/scenarios/progress/{progressId}/choices/{choiceId}", normalProgressId, normalChoiceId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, normalPlayerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loyaltyDelta").exists())
                .andExpect(jsonPath("$.safetyDelta").exists())
                .andExpect(jsonPath("$.loyaltyScore").exists())
                .andExpect(jsonPath("$.safetyScore").exists());
    }

    @Test
    void debriefIsLockedDuringExamAndScenarioCatalogStillWorksAsBefore() throws Exception {
        UUID playerId = UUID.randomUUID();

        String examBody = mockMvc.perform(post("/api/exams").header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID examId = UUID.fromString(objectMapper.readTree(examBody).get("examId").asText());

        String startBody = mockMvc.perform(post("/api/exams/{examId}/current", examId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID progressId = UUID.fromString(objectMapper.readTree(startBody).get("progressId").asText());

        mockMvc.perform(get("/api/feedback/debrief/{id}", progressId))
                .andExpect(status().isConflict());

        // Каталог сценариев (не тронут этой задачей) продолжает отдавать активные сценарии.
        mockMvc.perform(get("/api/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(51));
    }

    /**
     * Регрессионный тест на self-invocation баг {@code ExamService.recordScenarioCompleted}
     * (см. {@code progress/reports/bug-hunt.md}, находка №1): раньше метод вызывался как
     * {@code this.recordScenarioCompleted(...)} из {@code @TransactionalEventListener}-метода того
     * же класса, из-за чего {@code REQUIRES_NEW} не применялся (self-invocation идёт в обход
     * Spring-прокси) и пункт экзамена никогда не помечался завершённым — экзамен физически нельзя
     * было пройти целиком. Тест намеренно НЕ {@code @Transactional} и публикует событие изнутри
     * реальной REST-транзакции (как в проде — см. {@code ScenarioPlayService}), а не напрямую через
     * {@code ExamService.recordScenarioCompleted} (так делают модульные тесты в
     * {@code ExamServiceIntegrationTest}) — только так воспроизводится AFTER_COMMIT-путь, на
     * котором баг проявлялся.
     */
    @Test
    void examCanBeCompletedEndToEndThroughRestAndUnlocksDebriefAfter() throws Exception {
        UUID playerId = UUID.randomUUID();
        int examSize = 3;

        String examBody = mockMvc.perform(post("/api/exams").param("size", String.valueOf(examSize))
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID examId = UUID.fromString(objectMapper.readTree(examBody).get("examId").asText());

        UUID lastProgressId = null;
        for (int i = 0; i < examSize; i++) {
            String startBody = mockMvc.perform(post("/api/exams/{examId}/current", examId)
                            .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            lastProgressId = UUID.fromString(objectMapper.readTree(startBody).get("progressId").asText());
            playScenarioToCompletion(lastProgressId, playerId);

            // currentIndex должен вырасти сразу после коммита выбора, завершившего пункт — если
            // self-invocation баг вернулся, currentIndex здесь останется 0 на первой же итерации.
            String examAfter = mockMvc.perform(get("/api/exams/{examId}", examId)
                            .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(objectMapper.readTree(examAfter).get("currentIndex").asInt()).isEqualTo(i + 1);
        }

        mockMvc.perform(get("/api/exams/{examId}", examId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result").exists())
                .andExpect(jsonPath("$.result.grade").exists());

        // Разбор пройденного пункта был заблокирован (409) на предыдущем тесте, пока экзамен шёл —
        // теперь, когда экзамен завершён целиком, он открыт.
        mockMvc.perform(get("/api/feedback/debrief/{id}", lastProgressId))
                .andExpect(status().isOk());
    }

    /** Проходит сценарий первым доступным выбором на каждом узле, пока не наступит COMPLETED. */
    private void playScenarioToCompletion(UUID progressId, UUID playerId) throws Exception {
        for (int step = 0; step < 20; step++) {
            String body = mockMvc.perform(get("/api/scenarios/progress/{id}", progressId)
                            .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode node = objectMapper.readTree(body);
            if ("COMPLETED".equals(node.get("status").asText())) {
                return;
            }
            UUID choiceId = UUID.fromString(node.get("currentNode").get("choices").get(0).get("id").asText());
            mockMvc.perform(post("/api/scenarios/progress/{progressId}/choices/{choiceId}", progressId, choiceId)
                            .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                    .andExpect(status().isOk());
        }
        throw new IllegalStateException("Сценарий не завершился за 20 шагов: progressId=" + progressId);
    }
}
