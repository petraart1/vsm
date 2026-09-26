package ru.vsm.backend.scenario.web;

import static org.assertj.core.api.Assertions.assertThat;
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
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * «Портрет пассажира» (см. {@code CarClass}): один и тот же путь по графу сценария 01
 * (boarding-no-ticket) даёт разные итоговые шкалы лояльности в зависимости от класса вагона,
 * старт без явного {@code carClass} по умолчанию — {@code STANDARD} (обратная совместимость со
 * старыми клиентами), а узел {@code start} этого сценария показывает разный текст для
 * {@code FIRST}/{@code STANDARD} ({@code passengerPortraits} в seed-файле).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CarClassPlayIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScenarioRepository scenarioRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void startingWithoutCarClassDefaultsToStandard() throws Exception {
        UUID scenarioId = scenarioRepository.findByCode("boarding-no-ticket").orElseThrow().getId();
        UUID playerId = UUID.randomUUID();

        mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.carClass").value("STANDARD"));
    }

    @Test
    void samePathYieldsDifferentLoyaltyScoreForStandardVsFirst() throws Exception {
        UUID scenarioId = scenarioRepository.findByCode("boarding-no-ticket").orElseThrow().getId();

        int standardLoyalty = playOfferToCheckThenFoundInSystem(scenarioId, "STANDARD");
        int firstLoyalty = playOfferToCheckThenFoundInSystem(scenarioId, "FIRST");

        // Оба выбора на этом пути дают положительную loyaltyDelta (offer-to-check: +3,
        // found-in-system: +15) — FIRST множит положительные дельты на 0.8 (см. CarClass),
        // поэтому итоговая лояльность FIRST ниже, чем STANDARD (у которого множитель 1.0,
        // т.е. поведение не отличается от того, что было до этой фичи).
        assertThat(standardLoyalty).isEqualTo(18); // 3 + 15, как и в ScenarioPlayApiIntegrationTest
        assertThat(firstLoyalty).isEqualTo((int) Math.round(3 * 0.8) + (int) Math.round(15 * 0.8)); // 2 + 12 = 14
        assertThat(firstLoyalty).isLessThan(standardLoyalty);
    }

    @Test
    void entryNodeTextIsOverriddenPerCarClass() throws Exception {
        UUID scenarioId = scenarioRepository.findByCode("boarding-no-ticket").orElseThrow().getId();

        String firstResponse = mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, UUID.randomUUID().toString())
                        .param("carClass", "FIRST"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String firstText = objectMapper.readTree(firstResponse).get("currentNode").get("text").asText();
        assertThat(firstText).contains("класса «Первый»");

        String standardResponse = mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, UUID.randomUUID().toString())
                        .param("carClass", "STANDARD"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String standardText = objectMapper.readTree(standardResponse).get("currentNode").get("text").asText();

        assertThat(standardText).isNotEqualTo(firstText);
        assertThat(standardText).contains("взволнованно говорит");
    }

    /** Проходит start -&gt; offer-to-check -&gt; found-in-system, возвращает итоговый loyaltyScore. */
    private int playOfferToCheckThenFoundInSystem(UUID scenarioId, String carClass) throws Exception {
        UUID playerId = UUID.randomUUID();

        String startResponse = mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString())
                        .param("carClass", carClass))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.carClass").value(carClass))
                .andReturn().getResponse().getContentAsString();
        JsonNode startBody = objectMapper.readTree(startResponse);
        UUID progressId = UUID.fromString(startBody.get("progressId").asText());
        UUID offerToCheckChoiceId = findChoiceId(startBody.get("currentNode").get("choices"), "offer-to-check");

        String afterFirst = mockMvc.perform(post(
                        "/api/scenarios/progress/{progressId}/choices/{choiceId}", progressId, offerToCheckChoiceId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carClass").value(carClass))
                .andReturn().getResponse().getContentAsString();
        JsonNode afterFirstBody = objectMapper.readTree(afterFirst);
        UUID foundInSystemChoiceId = findChoiceId(afterFirstBody.get("nextNode").get("choices"), "found-in-system");

        String afterSecond = mockMvc.perform(post(
                        "/api/scenarios/progress/{progressId}/choices/{choiceId}", progressId, foundInSystemChoiceId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(afterSecond).get("loyaltyScore").asInt();
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
