package ru.vsm.backend.config.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
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
import ru.vsm.backend.scenario.web.ScenarioPlayController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Проверяет, что тело ошибки имеет одинаковую форму {@code {error, message, details?, timestamp,
 * path}} независимо от источника: framework-уровневые случаи из {@code GlobalExceptionHandler}
 * (валидация, невалидные параметры, неизвестный путь/метод, 401/403 от Spring Security) и уже
 * существующие доменные коды из auth/scenario ({@code login_already_taken}, {@code invalid_markdown},
 * {@code progress_already_completed}) — коды не изменились, изменилась только форма тела.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class GlobalErrorFormatIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.auth.admin.login}")
    private String adminLogin;

    @Value("${app.auth.admin.password}")
    private String adminPassword;

    // --- framework-уровневые случаи (GlobalExceptionHandler) ---

    @Test
    void unknownPathReturnsNotFoundWithUnifiedShape() throws Exception {
        mockMvc.perform(get("/api/does-not-exist-xyz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/does-not-exist-xyz"));
    }

    @Test
    void wrongHttpMethodReturnsMethodNotAllowedWithUnifiedShape() throws Exception {
        // /api/gamification/teams маплен только на GET.
        mockMvc.perform(delete("/api/gamification/teams"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("method_not_allowed"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/gamification/teams"));
    }

    @Test
    void invalidUuidPathVariableReturnsBadRequestWithUnifiedShape() throws Exception {
        // Модуль feedback: @PathVariable UUID, невалидное значение -> MethodArgumentTypeMismatchException.
        mockMvc.perform(get("/api/feedback/debrief/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/feedback/debrief/not-a-uuid"));
    }

    @Test
    void blankEsiaCodeReturnsValidationFailedWithFieldDetails() throws Exception {
        mockMvc.perform(post("/api/auth/esia/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("code")))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/auth/esia/callback"));
    }

    // --- Spring Security 401/403 (SecurityConfig.writeJsonError) ---

    @Test
    void missingAuthHeaderOnAdminEndpointReturnsUnauthorizedWithUnifiedShape() throws Exception {
        mockMvc.perform(post("/api/editor/scenarios/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/editor/scenarios/validate"));
    }

    @Test
    void nonAdminTokenOnAdminEndpointReturnsAccessDeniedWithUnifiedShape() throws Exception {
        String login = "usr-" + UUID.randomUUID().toString().substring(0, 8);
        String registerBody = """
                {"login":"%s","email":"%s@example.com","password":"password123","displayName":"Test"}
                """.formatted(login, login);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());
        String userToken = loginAndGetToken(login, "password123");

        mockMvc.perform(post("/api/editor/scenarios/validate")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("access_denied"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/editor/scenarios/validate"));
    }

    // --- уже существующие доменные коды: форма унифицирована, значения кодов не изменились ---

    @Test
    void duplicateLoginReturnsLoginAlreadyTakenWithUnifiedShape() throws Exception {
        String login = "dup-" + UUID.randomUUID().toString().substring(0, 8);
        String registerBody = """
                {"login":"%s","email":"%s@example.com","password":"password123","displayName":"Test"}
                """.formatted(login, login);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());

        String secondRegisterBody = """
                {"login":"%s","email":"other-%s@example.com","password":"password123","displayName":"Test2"}
                """.formatted(login, login);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondRegisterBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("login_already_taken"))
                .andExpect(jsonPath("$.message").value("login_already_taken"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    @Test
    void invalidMarkdownReturnsInvalidMarkdownWithDetailsList() throws Exception {
        String adminToken = loginAndGetToken(adminLogin, adminPassword);
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
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/editor/scenarios/import-markdown"));
    }

    @Test
    void choosingAgainAfterCompletionReturnsProgressAlreadyCompletedWithUnifiedShape() throws Exception {
        String adminToken = loginAndGetToken(adminLogin, adminPassword);
        String code = "error-format-" + UUID.randomUUID().toString().substring(0, 8);
        String markdown = """
                # Единственный шаг
                Код: %s
                Блок: misc

                ## start
                Текст узла.

                - [only] Единственный вариант -> end (лояльность +1, безопасность +1)

                ## end
                Итог.
                Итог: SUCCESS
                """.formatted(code);

        String importResponse = mockMvc.perform(post("/api/editor/scenarios/import-markdown")
                        .param("save", "true")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.valueOf("text/markdown"))
                        .content(markdown))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID scenarioId = UUID.fromString(objectMapper.readTree(importResponse).get("id").asText());

        UUID playerId = UUID.randomUUID();
        String startResponse = mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode startBody = objectMapper.readTree(startResponse);
        UUID progressId = UUID.fromString(startBody.get("progressId").asText());
        UUID choiceId = UUID.fromString(
                startBody.get("currentNode").get("choices").get(0).get("id").asText());

        mockMvc.perform(post("/api/scenarios/progress/{progressId}/choices/{choiceId}", progressId, choiceId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(post("/api/scenarios/progress/{progressId}/choices/{choiceId}", progressId, choiceId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("progress_already_completed"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value(
                        "/api/scenarios/progress/" + progressId + "/choices/" + choiceId));
    }

    private String loginAndGetToken(String login, String password) throws Exception {
        String loginBody = """
                {"login":"%s","password":"%s"}
                """.formatted(login, password);
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asString();
    }
}
