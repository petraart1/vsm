package ru.vsm.backend.gamification.admin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * CSV-выгрузка статистики администратора: доступ только с ролью {@code ADMIN} (та же защита
 * {@code /api/admin/**}, что и у JSON-эндпоинтов — см. {@code AdminRoleAuthorizationIntegrationTest}),
 * заголовки ответа и UTF-8 BOM в начале тела. Экранирование {@code ;}/{@code "} внутри значения
 * проверяется юнит-тестом {@code CsvExport} напрямую, без похода в БД.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminStatsCsvIntegrationTest {

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

    private String adminToken() throws Exception {
        String body = """
                {"login":"%s","password":"%s"}
                """.formatted(adminLogin, adminPassword);
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void scenariosCsvWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/stats/scenarios.csv"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void scenariosCsvWithAdminTokenReturnsCsvWithBomAndHeaderRow() throws Exception {
        String token = adminToken();

        MockHttpServletResponse response = mockMvc.perform(get("/api/admin/stats/scenarios.csv")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertContentTypeAndDisposition(response, "scenarios.csv");
        String text = bodyAfterBom(response);
        assertThat(text).startsWith(
                "code;title;block;totalPlaythroughs;completedPlaythroughs;successRate;avgLoyaltyScore;avgSafetyScore;timeoutRate\r\n");
    }

    @Test
    void blocksCsvWithAdminTokenReturnsCsvWithBomAndHeaderRow() throws Exception {
        String token = adminToken();

        MockHttpServletResponse response = mockMvc.perform(get("/api/admin/stats/blocks.csv")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertContentTypeAndDisposition(response, "blocks.csv");
        String text = bodyAfterBom(response);
        assertThat(text).startsWith(
                "block;totalPlaythroughs;completedPlaythroughs;successRate;avgLoyaltyScore;avgSafetyScore\r\n");
    }

    @Test
    void playersCsvWithAdminTokenReturnsCsvWithBomAndHeaderRow() throws Exception {
        String token = adminToken();

        MockHttpServletResponse response = mockMvc.perform(get("/api/admin/stats/players.csv")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertContentTypeAndDisposition(response, "players.csv");
        String text = bodyAfterBom(response);
        assertThat(text).startsWith(
                "playerId;displayName;team;totalScore;totalPlaythroughs;successRate;avgLoyaltyScore;avgSafetyScore;lastActivity\r\n");
    }

    @Test
    void teamsCsvWithAdminTokenReturnsCsvWithBomAndHeaderRow() throws Exception {
        String token = adminToken();

        MockHttpServletResponse response = mockMvc.perform(get("/api/admin/stats/teams.csv")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertContentTypeAndDisposition(response, "teams.csv");
        String text = bodyAfterBom(response);
        assertThat(text).startsWith(
                "rank;code;name;depot;memberCount;totalScore;averageScore;totalPlaythroughs;averageSafety\r\n");
    }

    private void assertContentTypeAndDisposition(MockHttpServletResponse response, String filename) {
        assertThat(response.getContentType()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(response.getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"" + filename + "\"");
    }

    private String bodyAfterBom(MockHttpServletResponse response) throws Exception {
        byte[] body = response.getContentAsByteArray();
        assertThat(body[0]).isEqualTo((byte) 0xEF);
        assertThat(body[1]).isEqualTo((byte) 0xBB);
        assertThat(body[2]).isEqualTo((byte) 0xBF);
        return new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
    }
}
