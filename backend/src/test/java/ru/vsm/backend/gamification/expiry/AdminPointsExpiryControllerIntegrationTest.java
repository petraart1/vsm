package ru.vsm.backend.gamification.expiry;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.gamification.domain.PlayerProfile;
import ru.vsm.backend.gamification.repository.PlayerProfileRepository;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.event.ScenarioCompletedEvent;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code POST /api/admin/points-expiry/run} — демо-запуск сгорания баллов: роль ADMIN обязательна
 * ({@code /api/admin/**}), опциональный {@code ?now=} симулирует дату, чтобы не ждать реальных
 * {@code inactivityDays} дней неактивности.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminPointsExpiryControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

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
    void anonymousCannotRunPointsExpiry() throws Exception {
        mockMvc.perform(post("/api/admin/points-expiry/run"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void adminRunWithSimulatedNowExpiresInactivePlayer() throws Exception {
        UUID playerId = UUID.randomUUID();
        eventPublisher.publishEvent(new ScenarioCompletedEvent(
                UUID.randomUUID(), playerId, UUID.randomUUID(), "boarding-no-ticket", "boarding",
                ScenarioOutcome.SUCCESS, 0, 0, 1, false, true,
                Instant.now().minusSeconds(30), Instant.now(), false, true));
        PlayerProfile profile = playerProfileRepository.findById(playerId).orElseThrow();
        profile.setTotalScore(500);
        profile.setLastActivityAt(Instant.now().minus(30, ChronoUnit.DAYS));
        playerProfileRepository.save(profile);

        String simulatedNow = Instant.now().toString();
        mockMvc.perform(post("/api/admin/points-expiry/run")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("now", simulatedNow))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiredPlayers").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        int scoreAfter = playerProfileRepository.findById(playerId).orElseThrow().getTotalScore();
        org.assertj.core.api.Assertions.assertThat(scoreAfter).isLessThan(500);
    }
}
