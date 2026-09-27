package ru.vsm.backend.scenario.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import ru.vsm.backend.scenario.domain.Scenario;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Административный список сценариев (включая неактивные) и переключатель доступности —
 * выключенный сценарий пропадает из {@code GET /api/scenarios}, но остаётся в
 * {@code GET /api/admin/scenarios}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminScenarioControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ScenarioRepository scenarioRepository;

    @Value("${app.auth.admin.login}")
    private String adminLogin;

    @Value("${app.auth.admin.password}")
    private String adminPassword;

    private String adminToken() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","password":"%s"}
                                """.formatted(adminLogin, adminPassword)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void adminScenariosWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/scenarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void adminTogglesScenarioActiveAndItDisappearsFromPublicCatalog() throws Exception {
        List<Scenario> all = scenarioRepository.findAll();
        assertThat(all).isNotEmpty();
        Scenario target = all.get(0);
        String code = target.getCode();
        String token = adminToken();

        try {
            mockMvc.perform(get("/api/admin/scenarios").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.code == '" + code + "')]").exists());

            mockMvc.perform(patch("/api/admin/scenarios/" + code)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false));

            mockMvc.perform(get("/api/scenarios"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.code == '" + code + "')]").doesNotExist());

            mockMvc.perform(get("/api/admin/scenarios").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.code == '" + code + "')]").exists());
        } finally {
            mockMvc.perform(patch("/api/admin/scenarios/" + code)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"active\":true}"));
        }
    }

    @Test
    void patchingUnknownScenarioIsNotFound() throws Exception {
        String token = adminToken();
        mockMvc.perform(patch("/api/admin/scenarios/no-such-code")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }
}
