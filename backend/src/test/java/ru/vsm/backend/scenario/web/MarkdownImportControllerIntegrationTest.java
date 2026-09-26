package ru.vsm.backend.scenario.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code POST /api/editor/scenarios/import-markdown}: корректная разметка -> валидный граф,
 * доступный в каталоге и проходимый до терминального узла; разметка с ошибками -> {@code 400
 * invalid_markdown} со списком "строка: причина" (не одна строка на весь ответ).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MarkdownImportControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScenarioRepository scenarioRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.auth.admin.login}")
    private String adminLogin;

    @Value("${app.auth.admin.password}")
    private String adminPassword;

    private String adminToken;

    @BeforeEach
    void loginAsAdmin() throws Exception {
        String loginBody = """
                {"login":"%s","password":"%s"}
                """.formatted(adminLogin, adminPassword);
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        adminToken = objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void validMarkdownIsSavedAndPlayableToTerminalNode() throws Exception {
        String code = "markdown-import-" + UUID.randomUUID().toString().substring(0, 8);
        String markdown = """
                # Ситуация из markdown
                Код: %s
                Блок: misc
                Флагман: нет
                Начальный узел: start

                ## start
                Пассажир из markdown просит пропустить его без очереди.

                - [refuse] Вежливо отказать и объяснить правило -> resolved (лояльность -2, безопасность +5)
                > Пояснение: правило соблюдено корректно.
                > Норма: dataset/standards/example.md
                > Шаги: признать, правило

                - [allow] Пропустить без очереди в обход правила -> failure (лояльность +8, безопасность -15)
                > Пояснение: нарушение правила ради сиюминутной лояльности.

                Таймер: 20 с, по умолчанию: refuse

                ## resolved
                Пассажир соглашается подождать своей очереди.
                Итог: SUCCESS

                ## failure
                Порядок нарушен, соседи по очереди жалуются.
                Итог: FAILURE
                """.formatted(code);

        String importResponse = mockMvc.perform(post("/api/editor/scenarios/import-markdown")
                        .param("save", "true")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.valueOf("text/markdown"))
                        .content(markdown))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andReturn().getResponse().getContentAsString();
        UUID scenarioId = UUID.fromString(objectMapper.readTree(importResponse).get("id").asText());

        assertThat(scenarioRepository.existsByCode(code)).isTrue();
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
        UUID refuseChoiceId = findChoiceId(startBody.get("currentNode").get("choices"), "refuse");

        mockMvc.perform(post(
                        "/api/scenarios/progress/{progressId}/choices/{choiceId}", progressId, refuseChoiceId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.finalOutcome").value("SUCCESS"))
                .andExpect(jsonPath("$.nextNode.code").value("resolved"));
    }

    @Test
    void previewWithoutSaveDoesNotPersistScenario() throws Exception {
        String code = "markdown-preview-" + UUID.randomUUID().toString().substring(0, 8);
        String markdown = """
                # Предпросмотр
                Код: %s
                Блок: misc

                ## start
                Текст узла.

                - [only] Единственный вариант -> end (лояльность +1, безопасность +1)

                ## end
                Итог.
                Итог: SUCCESS
                """.formatted(code);

        mockMvc.perform(post("/api/editor/scenarios/import-markdown")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.valueOf("text/markdown"))
                        .content(markdown))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario.code").value(code))
                .andExpect(jsonPath("$.errors.length()").value(0));

        assertThat(scenarioRepository.existsByCode(code)).isFalse();
    }

    @Test
    void jsonBodyWithMarkdownFieldIsAccepted() throws Exception {
        String code = "markdown-json-" + UUID.randomUUID().toString().substring(0, 8);
        String markdown = "# JSON-обёртка\n"
                + "Код: " + code + "\n"
                + "Блок: misc\n\n"
                + "## start\n"
                + "Текст узла.\n\n"
                + "- [only] Единственный вариант -> end (лояльность +1, безопасность +1)\n\n"
                + "## end\n"
                + "Итог.\n"
                + "Итог: SUCCESS\n";
        String jsonBody = objectMapper.writeValueAsString(new MarkdownRequest(markdown));

        mockMvc.perform(post("/api/editor/scenarios/import-markdown")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario.code").value(code));
    }

    @Test
    void malformedMarkdownReturns400WithLineAndReasonPerError() throws Exception {
        String markdown = """
                # Битая ситуация
                Блок: misc

                ## start
                Текст узла.

                - [bad] Вариант без дельт на шкалы -> target
                """;

        mockMvc.perform(post("/api/editor/scenarios/import-markdown")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.valueOf("text/markdown"))
                        .content(markdown))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_markdown"))
                .andExpect(jsonPath("$.details", hasItem(containsString("Код"))))
                .andExpect(jsonPath("$.details", hasItem(containsString("лояльность"))));
    }

    private UUID findChoiceId(JsonNode choices, String code) {
        for (JsonNode choice : choices) {
            if (choice.get("code").asText().equals(code)) {
                return UUID.fromString(choice.get("id").asText());
            }
        }
        throw new IllegalStateException("Вариант '" + code + "' не найден в ответе API");
    }

    private record MarkdownRequest(String markdown) {
    }
}
