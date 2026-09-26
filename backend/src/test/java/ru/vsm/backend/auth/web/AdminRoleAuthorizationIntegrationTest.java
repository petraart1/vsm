package ru.vsm.backend.auth.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import tools.jackson.databind.ObjectMapper;

/**
 * Шаг 3 авторизации: {@code /api/admin/**} и {@code /api/editor/**} — только роль ADMIN. Без
 * токена -> {@code 401}, с валидным токеном роли USER -> {@code 403}, с ролью ADMIN -> {@code 200}.
 * Оба статуса — JSON того же формата, что и остальные ошибки API ({@code {"error","message"}}),
 * не стандартная HTML/plain-text страница Spring Security.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminRoleAuthorizationIntegrationTest {

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

    private String login(String login, String password) throws Exception {
        String body = """
                {"login":"%s","password":"%s"}
                """.formatted(login, password);
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String registerAndLoginAsUser(String login) throws Exception {
        String registerBody = """
                {"login":"%s","email":"%s@example.com","password":"password123","displayName":"U"}
                """.formatted(login, login);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());
        return login(login, "password123");
    }

    @Test
    void editorWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/editor/template"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void editorWithUserRoleIsForbidden() throws Exception {
        String userToken = registerAndLoginAsUser("editor-user-role");

        mockMvc.perform(get("/api/editor/template").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("access_denied"));
    }

    @Test
    void editorWithAdminRoleIsAllowed() throws Exception {
        String adminToken = login(adminLogin, adminPassword);

        mockMvc.perform(get("/api/editor/template").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void adminStatsWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/stats/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void adminStatsWithUserRoleIsForbidden() throws Exception {
        String userToken = registerAndLoginAsUser("admin-stats-user-role");

        mockMvc.perform(get("/api/admin/stats/overview").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("access_denied"));
    }

    @Test
    void adminStatsWithAdminRoleIsAllowed() throws Exception {
        String adminToken = login(adminLogin, adminPassword);

        mockMvc.perform(get("/api/admin/stats/overview").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
