package ru.vsm.backend.scenario.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.scenario.domain.ScenarioChoice;
import ru.vsm.backend.scenario.domain.ScenarioChoiceHistory;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.event.ScenarioCompletedEvent;
import ru.vsm.backend.scenario.repository.ScenarioChoiceHistoryRepository;
import ru.vsm.backend.scenario.repository.ScenarioChoiceRepository;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Сквозной REST-тест нового флагманского сценария 06 «Пассажир с признаками алкогольного
 * опьянения» (см. {@code 06-intoxicated-passenger.json}): полное прохождение через
 * узел-эскалацию "вызов начальника поезда по рации" с выбором открытой формулировки
 * (скрытый штраф безопасности при loyaltyDelta = 0, т.к. пассажир этот разговор не слышит),
 * дальше до финального SUCCESS-узла с сопровождением ПТБ.
 *
 * <p>Заодно проверяет задачу №4 карточки: {@code explanation}/{@code normRef} выбора доступны
 * читающим доменам (feedback-analytics) напрямую через {@link ScenarioChoiceRepository} без
 * изменений контракта REST прохождения для frontend.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@RecordApplicationEvents
class ScenarioPlayIntoxicatedPassengerApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScenarioRepository scenarioRepository;

    @Autowired
    private ScenarioChoiceRepository scenarioChoiceRepository;

    @Autowired
    private ScenarioChoiceHistoryRepository scenarioChoiceHistoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullPlaythroughThroughRadioEscalationAppliesHiddenSafetyPenaltyAndReachesSuccess(
            ApplicationEvents events) throws Exception {
        UUID scenarioId = scenarioRepository.findByCode("intoxicated-passenger").orElseThrow().getId();
        UUID playerId = UUID.randomUUID();

        String startResponse = mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentNode.code").value("start"))
                .andReturn().getResponse().getContentAsString();
        JsonNode startBody = objectMapper.readTree(startResponse);
        UUID progressId = UUID.fromString(startBody.get("progressId").asText());
        UUID calmApproachId = findChoiceId(startBody.get("currentNode").get("choices"), "calm-approach");

        // Шкалы клампятся на [0, 100] (см. ScenarioPlayService.clampScale): счёт стартует с 0, а
        // raw loyaltyDelta -1 упирается в нижнюю границу — применённая дельта здесь 0, не -1.
        String afterStart = mockMvc.perform(post(
                        "/api/scenarios/progress/{progressId}/choices/{choiceId}", progressId, calmApproachId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loyaltyDelta").value(0))
                .andExpect(jsonPath("$.safetyDelta").value(6))
                .andExpect(jsonPath("$.loyaltyScore").value(0))
                .andExpect(jsonPath("$.safetyScore").value(6))
                .andExpect(jsonPath("$.nextNode.code").value("radio-call-chief"))
                .andReturn().getResponse().getContentAsString();
        JsonNode afterStartBody = objectMapper.readTree(afterStart);
        UUID openFormulationId =
                findChoiceId(afterStartBody.get("nextNode").get("choices"), "open-formulation");

        // Скрытая механика: пассажир этот разговор по рации не слышит (loyaltyDelta = 0 у обеих
        // формулировок), но открытая формулировка всё равно штрафует рейтинг безопасности.
        String afterRadio = mockMvc.perform(post(
                        "/api/scenarios/progress/{progressId}/choices/{choiceId}", progressId, openFormulationId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loyaltyDelta").value(0))
                .andExpect(jsonPath("$.safetyDelta").value(-6))
                .andExpect(jsonPath("$.loyaltyScore").value(0))
                .andExpect(jsonPath("$.safetyScore").value(0))
                .andExpect(jsonPath("$.nextNode.code").value("chief-arrives"))
                .andReturn().getResponse().getContentAsString();
        JsonNode afterRadioBody = objectMapper.readTree(afterRadio);
        UUID supportWithPtbId =
                findChoiceId(afterRadioBody.get("nextNode").get("choices"), "support-with-ptb");

        mockMvc.perform(post(
                        "/api/scenarios/progress/{progressId}/choices/{choiceId}", progressId, supportWithPtbId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loyaltyDelta").value(3))
                .andExpect(jsonPath("$.safetyDelta").value(10))
                .andExpect(jsonPath("$.loyaltyScore").value(3))
                .andExpect(jsonPath("$.safetyScore").value(10))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.finalOutcome").value("SUCCESS"))
                .andExpect(jsonPath("$.nextNode.code").value("resolved-escalated-ptb"));

        List<ScenarioChoiceHistory> history =
                scenarioChoiceHistoryRepository.findByUserProgressIdOrderBySequenceIndex(progressId);
        assertThat(history).hasSize(3);

        ScenarioCompletedEvent event = events.stream(ScenarioCompletedEvent.class).findFirst().orElseThrow();
        assertThat(event.outcome()).isEqualTo(ScenarioOutcome.SUCCESS);
        assertThat(event.loyaltyScore()).isEqualTo(3);
        assertThat(event.safetyScore()).isEqualTo(10);
        assertThat(event.choicesMade()).isEqualTo(3);

        // Явное объяснение и ссылка на норму заполнены и доступны read-only другим доменам
        // (feedback-analytics) через тот же репозиторий, без расширения REST-контракта прохождения.
        ScenarioChoice openFormulationChoice = scenarioChoiceRepository.findById(openFormulationId).orElseThrow();
        assertThat(openFormulationChoice.getExplanation()).contains("не слышит");
        assertThat(openFormulationChoice.getNormRef()).isNotBlank();
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
