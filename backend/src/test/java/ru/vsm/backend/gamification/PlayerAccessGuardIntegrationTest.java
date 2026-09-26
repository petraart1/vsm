package ru.vsm.backend.gamification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.event.ScenarioCompletedEvent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Доступ к личным игровым данным (профиль, уведомления, лидерборд) — см. находку CRITICAL в
 * аудите безопасности: раньше {@code playerId} в пути/query читался без единой проверки владения,
 * а публичный лидерборд отдавал реальные {@code playerId}, что вместе позволяло анониму,
 * знающему/угадавшему чужой id, полностью управлять его игровым состоянием. Проверяет
 * {@code PlayerAccessGuard} (владелец/ADMIN -&gt; 200, чужой -&gt; 403 {@code forbidden}) и
 * {@code publicId}/{@code me} в лидерборде ({@code GamificationQueryService}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PlayerAccessGuardIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ObjectMapper objectMapper;

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

    private UUID completeScenario(UUID playerId) {
        eventPublisher.publishEvent(new ScenarioCompletedEvent(
                UUID.randomUUID(), playerId, UUID.randomUUID(), "guard-test-" + UUID.randomUUID(),
                "boarding", ScenarioOutcome.SUCCESS, 10, 10, 3, false, true,
                Instant.now().minusSeconds(60), Instant.now(), false, true));
        return playerId;
    }

    @Test
    void ownProfileByHeaderIsAllowed() throws Exception {
        UUID playerId = completeScenario(UUID.randomUUID());

        mockMvc.perform(get("/api/gamification/profile/{playerId}", playerId)
                        .header("X-Player-Id", playerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(playerId.toString()));
    }

    @Test
    void foreignProfileIsForbidden() throws Exception {
        UUID playerId = completeScenario(UUID.randomUUID());

        mockMvc.perform(get("/api/gamification/profile/{playerId}", playerId)
                        .header("X-Player-Id", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void profileWithoutAnyIdentityIsForbidden() throws Exception {
        UUID playerId = completeScenario(UUID.randomUUID());

        mockMvc.perform(get("/api/gamification/profile/{playerId}", playerId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void adminCanReadForeignProfile() throws Exception {
        UUID playerId = completeScenario(UUID.randomUUID());
        String token = adminToken();

        mockMvc.perform(get("/api/gamification/profile/{playerId}", playerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void notificationsListReadAndReadAllRequireOwnership() throws Exception {
        UUID playerId = completeScenario(UUID.randomUUID());

        mockMvc.perform(get("/api/gamification/notifications")
                        .param("playerId", playerId.toString())
                        .header("X-Player-Id", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        String listResponse = mockMvc.perform(get("/api/gamification/notifications")
                        .param("playerId", playerId.toString())
                        .header("X-Player-Id", playerId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID notificationId = UUID.fromString(objectMapper.readTree(listResponse).get(0).get("id").asString());

        mockMvc.perform(post("/api/gamification/notifications/{id}/read", notificationId)
                        .header("X-Player-Id", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/gamification/notifications/{id}/read", notificationId)
                        .header("X-Player-Id", playerId.toString()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/gamification/notifications/read-all")
                        .param("playerId", playerId.toString())
                        .header("X-Player-Id", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void leaderboardHidesRealPlayerIdAndMarksOwnRow() throws Exception {
        UUID playerId = completeScenario(UUID.randomUUID());

        String response = mockMvc.perform(get("/api/gamification/leaderboard")
                        .param("limit", "50")
                        .header("X-Player-Id", playerId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(playerId.toString());

        JsonNode node = objectMapper.readTree(response);
        JsonNode meEntry = node.get("me");
        assertThat(meEntry).isNotNull();
        assertThat(meEntry.isNull()).isFalse();
        assertThat(meEntry.get("me").asBoolean()).isTrue();
        assertThat(meEntry.get("publicId").asString()).isNotBlank();
        assertThat(meEntry.has("playerId")).isFalse();

        boolean anyMeInTop = false;
        for (JsonNode entry : node.get("top")) {
            assertThat(entry.has("playerId")).isFalse();
            if (entry.get("publicId").asString().equals(meEntry.get("publicId").asString())) {
                anyMeInTop = true;
                assertThat(entry.get("me").asBoolean()).isTrue();
            }
        }
        assertThat(anyMeInTop).isTrue();
    }

    @Test
    void leaderboardWithoutIdentityHasNullMe() throws Exception {
        completeScenario(UUID.randomUUID());

        String response = mockMvc.perform(get("/api/gamification/leaderboard"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(response).get("me").isNull()).isTrue();
    }
}
