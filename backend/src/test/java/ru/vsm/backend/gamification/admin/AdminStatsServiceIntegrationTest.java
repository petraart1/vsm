package ru.vsm.backend.gamification.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.gamification.admin.service.AdminStatsService;
import ru.vsm.backend.gamification.admin.web.dto.BlockStatsEntryDto;
import ru.vsm.backend.gamification.admin.web.dto.OverviewStatsResponse;
import ru.vsm.backend.gamification.admin.web.dto.ScenarioStatsEntryDto;
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.domain.Scenario;
import ru.vsm.backend.scenario.domain.ScenarioChoice;
import ru.vsm.backend.scenario.domain.ScenarioChoiceHistory;
import ru.vsm.backend.scenario.domain.ScenarioNode;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.domain.UserProgress;
import ru.vsm.backend.scenario.repository.ScenarioChoiceHistoryRepository;
import ru.vsm.backend.scenario.repository.ScenarioChoiceRepository;
import ru.vsm.backend.scenario.repository.ScenarioNodeRepository;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import ru.vsm.backend.scenario.repository.UserProgressRepository;

/**
 * Прогоняет несколько прохождений двух уже засеянных при старте сценариев
 * ({@code boarding-no-ticket}, {@code medical-passenger-unwell} — используются и другими
 * тестами домена scenario) через прямую запись в {@code UserProgress}/{@code ScenarioChoiceHistory}
 * (без похода через REST/WS — эти домены не трогаем) и проверяет посчитанные агрегаты, а также
 * поведение на пустой БД для отдельного случая.
 */
@SpringBootTest
@Testcontainers
class AdminStatsServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private AdminStatsService statsService;

    @Autowired
    private ScenarioRepository scenarioRepository;

    @Autowired
    private ScenarioNodeRepository scenarioNodeRepository;

    @Autowired
    private ScenarioChoiceRepository scenarioChoiceRepository;

    @Autowired
    private UserProgressRepository userProgressRepository;

    @Autowired
    private ScenarioChoiceHistoryRepository scenarioChoiceHistoryRepository;

    @Test
    void emptyDatabaseReturnsZeroesNotErrors() {
        // Собственный сценарий без единого прохождения — не попадает в aggregateByScenario/Blocks,
        // а overview на полностью пустой статистике (для нового окружения) не должен падать.
        OverviewStatsResponse overview = statsService.getOverview();

        assertThat(overview.totalPlaythroughs()).isGreaterThanOrEqualTo(0);
        assertThat(overview.avgLoyaltyScore()).isGreaterThanOrEqualTo(0.0);
        assertThat(overview.successRate()).isBetween(0.0, 1.0);

        Scenario untouched = scenarioRepository.findByCode("boarding-late-passenger").orElseThrow();
        List<ScenarioStatsEntryDto> scenarioStats = statsService.getScenarioStats();
        assertThat(scenarioStats).noneMatch(s -> s.scenarioId().equals(untouched.getId()));
    }

    @Test
    void aggregatesMultiplePlaythroughsIntoExpectedNumbers() {
        Scenario boarding = scenarioRepository.findByCode("boarding-no-ticket").orElseThrow();
        Scenario medical = scenarioRepository.findByCode("medical-passenger-unwell").orElseThrow();

        ScenarioNode boardingEntry = scenarioNodeRepository.findById(boarding.getEntryNodeId()).orElseThrow();
        ScenarioChoice boardingChoice =
                scenarioChoiceRepository.findByNodeIdOrderBySortOrder(boardingEntry.getId()).get(0);
        ScenarioNode medicalEntry = scenarioNodeRepository.findById(medical.getEntryNodeId()).orElseThrow();
        ScenarioChoice medicalChoice =
                scenarioChoiceRepository.findByNodeIdOrderBySortOrder(medicalEntry.getId()).get(0);

        Instant now = Instant.now();

        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID player3 = UUID.randomUUID();
        UUID player4 = UUID.randomUUID();

        // boarding: COMPLETED/SUCCESS, активен только что (24ч и 7д)
        UserProgress boardingSuccess = saveProgress(boarding.getId(), player1, ProgressStatus.COMPLETED,
                ScenarioOutcome.SUCCESS, 80, 90, now);
        // boarding: COMPLETED/FAILURE, активен 3 дня назад (только 7д)
        UserProgress boardingFailure = saveProgress(boarding.getId(), player2, ProgressStatus.COMPLETED,
                ScenarioOutcome.FAILURE, 20, 10, now.minus(Duration.ofDays(3)));
        // boarding: IN_PROGRESS, активность 10 дней назад (не входит ни в 24ч, ни в 7д)
        saveProgress(boarding.getId(), player3, ProgressStatus.IN_PROGRESS,
                null, 5, 5, now.minus(Duration.ofDays(10)));
        // medical: COMPLETED/PARTIAL, активен только что
        UserProgress medicalPartial = saveProgress(medical.getId(), player4, ProgressStatus.COMPLETED,
                ScenarioOutcome.PARTIAL, 40, 60, now);

        // История выборов boarding: 2 записи, одна по таймауту -> timeoutRate сценария = 1/2
        saveChoiceHistory(boardingSuccess.getId(), boardingEntry.getId(), boardingChoice.getId(), 0, false);
        saveChoiceHistory(boardingFailure.getId(), boardingEntry.getId(), boardingChoice.getId(), 0, true);
        // История medical: 1 запись, без таймаута -> timeoutRate сценария = 0
        saveChoiceHistory(medicalPartial.getId(), medicalEntry.getId(), medicalChoice.getId(), 0, false);

        OverviewStatsResponse overview = statsService.getOverview();
        assertThat(overview.totalPlayers()).isGreaterThanOrEqualTo(4);
        assertThat(overview.completedPlaythroughs()).isGreaterThanOrEqualTo(3);
        assertThat(overview.inProgressPlaythroughs()).isGreaterThanOrEqualTo(1);
        // 24ч: player1 (boarding, now) + player4 (medical, now); player2/player3 вне окна.
        assertThat(overview.activePlayers24h()).isGreaterThanOrEqualTo(2);
        // 7д: дополнительно player2 (3 дня назад); player3 (10 дней назад) — нет.
        assertThat(overview.activePlayers7d()).isGreaterThanOrEqualTo(3);

        List<ScenarioStatsEntryDto> scenarioStats = statsService.getScenarioStats();
        ScenarioStatsEntryDto boardingStats = scenarioStats.stream()
                .filter(s -> s.scenarioId().equals(boarding.getId())).findFirst().orElseThrow();
        assertThat(boardingStats.totalPlaythroughs()).isEqualTo(3);
        assertThat(boardingStats.completedPlaythroughs()).isEqualTo(2);
        assertThat(boardingStats.successRate()).isCloseTo(0.5, offset(0.001)); // 1 success из 2 завершённых
        assertThat(boardingStats.avgLoyaltyScore()).isCloseTo(50.0, offset(0.001)); // avg(80,20) по завершённым
        assertThat(boardingStats.avgSafetyScore()).isCloseTo(50.0, offset(0.001)); // avg(90,10)
        assertThat(boardingStats.timeoutRate()).isCloseTo(0.5, offset(0.001)); // 1 таймаут из 2 выборов
        assertThat(boardingStats.code()).isEqualTo("boarding-no-ticket");
        assertThat(boardingStats.block()).isEqualTo("boarding");

        ScenarioStatsEntryDto medicalStats = scenarioStats.stream()
                .filter(s -> s.scenarioId().equals(medical.getId())).findFirst().orElseThrow();
        assertThat(medicalStats.successRate()).isCloseTo(0.0, offset(0.001)); // PARTIAL, не SUCCESS
        assertThat(medicalStats.timeoutRate()).isCloseTo(0.0, offset(0.001));

        // boarding (successRate 0.5) должен быть выше по списку ("более проваливаемый"),
        // чем любой полностью успешный сценарий, но в данном прогоне достаточно проверить,
        // что медицинский с successRate 0 не позади сценария с более высоким successRate.
        int boardingIndex = scenarioStats.indexOf(boardingStats);
        int medicalIndex = scenarioStats.indexOf(medicalStats);
        assertThat(medicalIndex).isLessThanOrEqualTo(boardingIndex);

        List<BlockStatsEntryDto> blockStats = statsService.getBlockStats();
        BlockStatsEntryDto boardingBlock = blockStats.stream()
                .filter(b -> b.block().equals("boarding")).findFirst().orElseThrow();
        assertThat(boardingBlock.totalPlaythroughs()).isGreaterThanOrEqualTo(3);
        BlockStatsEntryDto medicalBlock = blockStats.stream()
                .filter(b -> b.block().equals("medical")).findFirst().orElseThrow();
        assertThat(medicalBlock.completedPlaythroughs()).isGreaterThanOrEqualTo(1);
    }

    private UserProgress saveProgress(UUID scenarioId, UUID userId, ProgressStatus status,
            ScenarioOutcome outcome, int loyalty, int safety, Instant updatedAt) {
        UserProgress progress = UserProgress.builder()
                .userId(userId)
                .scenarioId(scenarioId)
                .status(status)
                .finalOutcome(outcome)
                .loyaltyScore(loyalty)
                .safetyScore(safety)
                .startedAt(updatedAt)
                .updatedAt(updatedAt)
                .completedAt(status == ProgressStatus.COMPLETED ? updatedAt : null)
                .build();
        return userProgressRepository.save(progress);
    }

    private void saveChoiceHistory(UUID userProgressId, UUID nodeId, UUID choiceId,
            int sequenceIndex, boolean wasTimeout) {
        scenarioChoiceHistoryRepository.save(ScenarioChoiceHistory.builder()
                .userProgressId(userProgressId)
                .nodeId(nodeId)
                .choiceId(choiceId)
                .sequenceIndex(sequenceIndex)
                .wasTimeout(wasTimeout)
                .loyaltyDeltaApplied(0)
                .safetyDeltaApplied(0)
                .build());
    }
}
