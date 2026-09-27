package ru.vsm.backend.gamification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
import ru.vsm.backend.gamification.domain.NotificationType;
import ru.vsm.backend.gamification.service.NotificationService;
import ru.vsm.backend.gamification.web.dto.NotificationDto;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.event.ScenarioCompletedEvent;
import ru.vsm.backend.scenario.seed.ScenarioSeedDto;
import ru.vsm.backend.scenario.seed.ScenarioSeedTemplateFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * Широковещательные уведомления, рассылаемые всем уже известным профилям игрока (в отличие от
 * {@link NotificationIntegrationTest}, где уведомления персональные): {@code NEW_SCENARIO} при
 * публикации нового сценария через редактор ({@code EditorScenarioController} ->
 * {@code ScenarioPublishedEvent} -> {@code ScenarioPublishedEventListener}) и
 * {@code NEW_CHALLENGE} при создании события админом ({@code AdminChallengeController}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BroadcastNotificationIntegrationTest {

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
    private NotificationService notificationService;

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

    /** Делает игрока "известным" (создаёт строку профиля), не завязываясь на конкретный сценарий. */
    private UUID knownPlayer() {
        UUID playerId = UUID.randomUUID();
        eventPublisher.publishEvent(new ScenarioCompletedEvent(
                UUID.randomUUID(), playerId, UUID.randomUUID(), "boarding-no-ticket", "boarding",
                ScenarioOutcome.SUCCESS, 5, 5, 2, false, true,
                Instant.now().minusSeconds(30), Instant.now(), false, true));
        return playerId;
    }

    @Test
    void publishingNewScenarioNotifiesKnownPlayersOnceAndNotOnUpdate() throws Exception {
        UUID playerId = knownPlayer();
        String token = adminToken();

        String code = "broadcast-new-scenario-" + UUID.randomUUID().toString().substring(0, 8);
        ScenarioSeedDto dto = ScenarioSeedTemplateFactory.build();
        dto.setCode(code);
        dto.setTitle("Новая ситуация для уведомления");

        mockMvc.perform(post("/api/editor/scenarios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        List<NotificationDto> afterCreate = notificationService.list(playerId, false);
        assertThat(afterCreate).extracting(NotificationDto::type)
                .filteredOn(NotificationType.NEW_SCENARIO.name()::equals)
                .hasSize(1);

        // Обновление уже существующего сценария (не создание) не должно рассылать ещё раз.
        dto.setTitle("Обновлённый заголовок");
        mockMvc.perform(post("/api/editor/scenarios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        List<NotificationDto> afterUpdate = notificationService.list(playerId, false);
        assertThat(afterUpdate).extracting(NotificationDto::type)
                .filteredOn(NotificationType.NEW_SCENARIO.name()::equals)
                .hasSize(1);
    }

    @Test
    void creatingChallengeAsAdminNotifiesKnownPlayers() throws Exception {
        UUID playerId = knownPlayer();
        String token = adminToken();

        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end = Instant.now().plus(7, ChronoUnit.DAYS);
        String body = """
                {"title":"Уведомление о событии","description":"Проверка рассылки NEW_CHALLENGE",
                 "goalType":"BLOCK_SCENARIOS_NO_FAILURE","targetBlock":"safety","targetCount":3,
                 "startsAt":"%s","endsAt":"%s","rewardPoints":100,"rewardAchievementCode":null}
                """.formatted(start, end);

        mockMvc.perform(post("/api/admin/challenges")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        assertThat(notificationService.list(playerId, false))
                .extracting(NotificationDto::type)
                .contains(NotificationType.NEW_CHALLENGE.name());
    }
}
