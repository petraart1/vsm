package ru.vsm.backend.feedback;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import ru.vsm.backend.feedback.web.CompetencyAnalyticsController;

/**
 * {@code GET /api/feedback/competencies/{playerId}} проверяет владение через
 * {@code PlayerAccessGuard} (см. javadoc {@link CompetencyAnalyticsController} и остаток
 * находки CRITICAL в аудите безопасности — раньше эта аналитика читалась без единой проверки).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CompetencyAnalyticsAccessIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ownCompetencyAnalyticsByHeaderIsAllowed() throws Exception {
        UUID playerId = UUID.randomUUID();

        mockMvc.perform(get("/api/feedback/competencies/{playerId}", playerId)
                        .header("X-Player-Id", playerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(playerId.toString()));
    }

    @Test
    void foreignCompetencyAnalyticsIsForbidden() throws Exception {
        UUID playerId = UUID.randomUUID();

        mockMvc.perform(get("/api/feedback/competencies/{playerId}", playerId)
                        .header("X-Player-Id", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void competencyAnalyticsWithoutAnyIdentityIsForbidden() throws Exception {
        UUID playerId = UUID.randomUUID();

        mockMvc.perform(get("/api/feedback/competencies/{playerId}", playerId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }
}
