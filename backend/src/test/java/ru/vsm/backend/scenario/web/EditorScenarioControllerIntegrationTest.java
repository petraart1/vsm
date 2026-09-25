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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import ru.vsm.backend.scenario.repository.UserProgressRepository;
import ru.vsm.backend.scenario.seed.ScenarioSeedDto;
import ru.vsm.backend.scenario.seed.ScenarioSeedTemplateFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Сквозной тест API редактора сценариев (критерий ТЗ "расширяемость" — добавить новую ситуацию
 * без пересборки и сразу пройти её): validate с битым графом, create -> сразу видна в каталоге и
 * проходима через {@code ScenarioPlayController} до терминала, update существующего без
 * прохождений, update сценария с прохождениями -> 409, export/import round-trip.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EditorScenarioControllerIntegrationTest {

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
    private ObjectMapper objectMapper;

    @Test
    void templateEndpointReturnsPlayableSkeleton() throws Exception {
        mockMvc.perform(get("/api/editor/template"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entryNode").value("start"))
                .andExpect(jsonPath("$.nodes[0].choices.length()").value(3));
    }

    @Test
    void validateReportsErrorsForBrokenGraphWithoutSaving() throws Exception {
        ScenarioSeedDto dto = ScenarioSeedTemplateFactory.build();
        dto.setCode("editor-validate-broken");
        // Ломаем граф: choice ссылается на несуществующий узел, второй узел никогда не встречается.
        dto.getNodes().get(0).getChoices().get(0).setTarget("does-not-exist");

        mockMvc.perform(post("/api/editor/scenarios/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        assertThat(scenarioRepository.existsByCode("editor-validate-broken")).isFalse();
    }

    @Test
    void createAppearsInCatalogAndIsPlayableToTerminalNode() throws Exception {
        String code = "editor-create-" + UUID.randomUUID().toString().substring(0, 8);
        ScenarioSeedDto dto = ScenarioSeedTemplateFactory.build();
        dto.setCode(code);
        dto.setTitle("Новая ситуация из редактора");

        String createResponse = mockMvc.perform(post("/api/editor/scenarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andReturn().getResponse().getContentAsString();
        UUID scenarioId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        mockMvc.perform(get("/api/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == '" + code + "')]").exists());

        UUID playerId = UUID.randomUUID();
        String startResponse = mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentNode.code").value("start"))
                .andReturn().getResponse().getContentAsString();
        JsonNode startBody = objectMapper.readTree(startResponse);
        UUID progressId = UUID.fromString(startBody.get("progressId").asText());
        UUID fullRoleModelChoiceId = findChoiceId(startBody.get("currentNode").get("choices"), "full-role-model");

        mockMvc.perform(post(
                        "/api/scenarios/progress/{progressId}/choices/{choiceId}", progressId, fullRoleModelChoiceId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.finalOutcome").value("SUCCESS"))
                .andExpect(jsonPath("$.nextNode.code").value("resolved-well"));
    }

    @Test
    void updateExistingScenarioWithoutPlaythroughsSucceeds() throws Exception {
        String code = "editor-update-" + UUID.randomUUID().toString().substring(0, 8);
        ScenarioSeedDto dto = ScenarioSeedTemplateFactory.build();
        dto.setCode(code);

        mockMvc.perform(post("/api/editor/scenarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        dto.setTitle("Обновлённый заголовок");
        mockMvc.perform(post("/api/editor/scenarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Обновлённый заголовок"));

        mockMvc.perform(get("/api/editor/scenarios/{code}", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Обновлённый заголовок"))
                .andExpect(jsonPath("$.nodes.length()").value(4));
    }

    @Test
    void updateScenarioWithExistingPlaythroughsIsRejectedWithConflict() throws Exception {
        String code = "editor-conflict-" + UUID.randomUUID().toString().substring(0, 8);
        ScenarioSeedDto dto = ScenarioSeedTemplateFactory.build();
        dto.setCode(code);

        String createResponse = mockMvc.perform(post("/api/editor/scenarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID scenarioId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isCreated());
        assertThat(userProgressRepository.existsByScenarioId(scenarioId)).isTrue();

        dto.setTitle("Попытка правки уже пройденного сценария");
        mockMvc.perform(post("/api/editor/scenarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("scenario_has_playthroughs"));
    }

    @Test
    void exportImportRoundTripPreservesGraphShape() throws Exception {
        String code = "editor-roundtrip-" + UUID.randomUUID().toString().substring(0, 8);
        ScenarioSeedDto dto = ScenarioSeedTemplateFactory.build();
        dto.setCode(code);

        mockMvc.perform(post("/api/editor/scenarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        String exported = mockMvc.perform(get("/api/editor/scenarios/{code}", code))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ScenarioSeedDto reimported = objectMapper.readValue(exported, ScenarioSeedDto.class);
        assertThat(reimported.getCode()).isEqualTo(code);
        assertThat(reimported.getEntryNode()).isEqualTo("start");
        assertThat(reimported.getNodes()).hasSize(4);

        // Повторное сохранение того же экспортированного графа (без изменений) должно пройти как
        // штатное обновление без прохождений, а не свалиться на неожиданном формате.
        mockMvc.perform(post("/api/editor/scenarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reimported)))
                .andExpect(status().isOk());
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
