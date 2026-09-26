package ru.vsm.backend.gamification.challenge;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.gamification.challenge.domain.Challenge;
import ru.vsm.backend.gamification.challenge.domain.ChallengeGoalType;
import ru.vsm.backend.gamification.challenge.repository.ChallengeRepository;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.event.ScenarioCompletedEvent;

/**
 * GET /api/gamification/challenges?playerId= — активные челленджи с прогрессом игрока
 * (см. {@code ChallengeAccrualIntegrationTest} для логики начисления прогресса).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ChallengeControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChallengeRepository challengeRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Test
    void returnsActiveChallengeWithPlayerProgress() throws Exception {
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.now();
        Challenge challenge = challengeRepository.save(Challenge.builder()
                .code("web-test-" + UUID.randomUUID())
                .title("Тестовый челлендж API")
                .description("Для проверки REST-эндпоинта")
                .goalType(ChallengeGoalType.BLOCK_SCENARIOS_NO_FAILURE)
                .targetBlock("boarding")
                .targetCount(2)
                .rewardPoints(50)
                .startsAt(now.minus(1, ChronoUnit.DAYS))
                .endsAt(now.plus(1, ChronoUnit.DAYS))
                .build());

        eventPublisher.publishEvent(new ScenarioCompletedEvent(
                UUID.randomUUID(), playerId, UUID.randomUUID(), "scenario-x", "boarding",
                ScenarioOutcome.SUCCESS, 10, 10, 3, false, true,
                now.minusSeconds(60), now, false, true));

        mockMvc.perform(get("/api/gamification/challenges").param("playerId", playerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == '" + challenge.getCode() + "')].current").value(1))
                .andExpect(jsonPath("$[?(@.code == '" + challenge.getCode() + "')].completed").value(false))
                .andExpect(jsonPath("$[?(@.code == '" + challenge.getCode() + "')].targetCount").value(2));
    }

    @Test
    void returnsZeroProgressWithoutPlayerId() throws Exception {
        Instant now = Instant.now();
        Challenge challenge = challengeRepository.save(Challenge.builder()
                .code("web-test-anon-" + UUID.randomUUID())
                .title("Тестовый челлендж без игрока")
                .description("Для проверки каталога без playerId")
                .goalType(ChallengeGoalType.ROLE_MODEL_ALL_STEPS)
                .targetCount(5)
                .rewardPoints(80)
                .startsAt(now.minus(1, ChronoUnit.DAYS))
                .endsAt(now.plus(1, ChronoUnit.DAYS))
                .build());

        mockMvc.perform(get("/api/gamification/challenges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == '" + challenge.getCode() + "')].current").value(0))
                .andExpect(jsonPath("$[?(@.code == '" + challenge.getCode() + "')].completed").value(false));
    }
}
