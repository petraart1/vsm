package ru.vsm.backend.feedback;

import static org.assertj.core.api.Assertions.assertThat;

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
import ru.vsm.backend.feedback.dto.BlockCompetencyStatsDto;
import ru.vsm.backend.feedback.dto.CompetencyAnalyticsResponse;
import ru.vsm.backend.feedback.dto.NormViolationDto;
import ru.vsm.backend.feedback.dto.RecommendationReason;
import ru.vsm.backend.feedback.dto.RoleStep;
import ru.vsm.backend.feedback.dto.RoleStepComplianceDto;
import ru.vsm.backend.feedback.dto.ScenarioRecommendationDto;
import ru.vsm.backend.feedback.service.CompetencyAnalyticsService;
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
 * Проверяет агрегацию компетенций по нескольким завершённым прохождениям на уже засеянных
 * сценариях {@code boarding-no-ticket} (block {@code boarding}) и {@code medical-passenger-unwell}
 * (block {@code medical}) — загружены {@code ScenarioSeedLoader} при старте контекста.
 *
 * <p>История выборов вставляется в БД напрямую через репозитории (read-only использование
 * scenario-домена), имитируя уже завершённые прохождения.
 */
@SpringBootTest
@Testcontainers
class CompetencyAnalyticsServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private ScenarioRepository scenarioRepository;
    @Autowired
    private ScenarioNodeRepository scenarioNodeRepository;
    @Autowired
    private ScenarioChoiceRepository scenarioChoiceRepository;
    @Autowired
    private UserProgressRepository userProgressRepository;
    @Autowired
    private ScenarioChoiceHistoryRepository historyRepository;
    @Autowired
    private CompetencyAnalyticsService competencyAnalyticsService;

    @Test
    void analyzeFlagsWeakBlockAndRecommendsUnplayedAndFailedScenarios() {
        UUID playerId = UUID.randomUUID();

        // Прохождение 1: boarding-no-ticket, нарушение норматива посадки без билета -> FAILURE.
        Scenario boarding = scenarioRepository.findByCode("boarding-no-ticket").orElseThrow();
        ScenarioNode boardingStart = nodeByCode(boarding.getId(), "start");
        ScenarioChoice letThroughFriendly = choiceByCode(boardingStart.getId(), "let-through-friendly");

        UserProgress boardingRun = userProgressRepository.save(UserProgress.builder()
                .userId(playerId)
                .scenarioId(boarding.getId())
                .status(ProgressStatus.COMPLETED)
                .loyaltyScore(letThroughFriendly.getLoyaltyDelta())
                .safetyScore(letThroughFriendly.getSafetyDelta())
                .finalOutcome(ScenarioOutcome.FAILURE)
                .completedAt(Instant.now())
                .build());
        historyRepository.save(ScenarioChoiceHistory.builder()
                .userProgressId(boardingRun.getId())
                .nodeId(boardingStart.getId())
                .choiceId(letThroughFriendly.getId())
                .sequenceIndex(0)
                .loyaltyDeltaApplied(letThroughFriendly.getLoyaltyDelta())
                .safetyDeltaApplied(letThroughFriendly.getSafetyDelta())
                .build());

        // Прохождение 2: medical-passenger-unwell, оба выбора закрывают почти все шаги ролевой
        // модели -> SUCCESS.
        Scenario medical = scenarioRepository.findByCode("medical-passenger-unwell").orElseThrow();
        ScenarioNode medicalStart = nodeByCode(medical.getId(), "start");
        ScenarioChoice askDoctorAndCallChief = choiceByCode(medicalStart.getId(), "ask-doctor-and-call-chief");
        ScenarioNode callChiefAndDoctor = nodeByCode(medical.getId(), "call-chief-and-doctor");
        ScenarioChoice coordinate = choiceByCode(callChiefAndDoctor.getId(), "coordinate");

        UserProgress medicalRun = userProgressRepository.save(UserProgress.builder()
                .userId(playerId)
                .scenarioId(medical.getId())
                .status(ProgressStatus.COMPLETED)
                .loyaltyScore(askDoctorAndCallChief.getLoyaltyDelta() + coordinate.getLoyaltyDelta())
                .safetyScore(askDoctorAndCallChief.getSafetyDelta() + coordinate.getSafetyDelta())
                .finalOutcome(ScenarioOutcome.SUCCESS)
                .completedAt(Instant.now())
                .build());
        historyRepository.save(ScenarioChoiceHistory.builder()
                .userProgressId(medicalRun.getId())
                .nodeId(medicalStart.getId())
                .choiceId(askDoctorAndCallChief.getId())
                .sequenceIndex(0)
                .loyaltyDeltaApplied(askDoctorAndCallChief.getLoyaltyDelta())
                .safetyDeltaApplied(askDoctorAndCallChief.getSafetyDelta())
                .build());
        historyRepository.save(ScenarioChoiceHistory.builder()
                .userProgressId(medicalRun.getId())
                .nodeId(callChiefAndDoctor.getId())
                .choiceId(coordinate.getId())
                .sequenceIndex(1)
                .loyaltyDeltaApplied(coordinate.getLoyaltyDelta())
                .safetyDeltaApplied(coordinate.getSafetyDelta())
                .build());

        CompetencyAnalyticsResponse response = competencyAnalyticsService.analyze(playerId);

        assertThat(response.playerId()).isEqualTo(playerId);
        assertThat(response.totalPlaythroughs()).isEqualTo(2);

        // boarding: 0% success, 100% failure -> blockScore -1, самый просевший из двух блоков.
        // medical: 100% success -> blockScore 1.0, не просевший (правило: blockScore < 1.0).
        assertThat(response.weakCompetencies()).containsExactly("boarding");

        BlockCompetencyStatsDto boardingStats = blockStats(response, "boarding");
        assertThat(boardingStats.playthroughs()).isEqualTo(1);
        assertThat(boardingStats.successRate()).isZero();
        assertThat(boardingStats.failureRate()).isEqualTo(1.0);
        assertThat(boardingStats.weak()).isTrue();

        BlockCompetencyStatsDto medicalStats = blockStats(response, "medical");
        assertThat(medicalStats.successRate()).isEqualTo(1.0);
        assertThat(medicalStats.failureRate()).isZero();
        assertThat(medicalStats.weak()).isFalse();

        // Ролевая модель: 3 сделанных выбора всего, let-through-friendly не закрывает ни одного
        // шага, оба медицинских выбора закрывают acknowledge/solution/reassure, только coordinate
        // дополнительно закрывает rule.
        RoleStepComplianceDto acknowledge = roleStep(response, RoleStep.ACKNOWLEDGE);
        assertThat(acknowledge.timesFollowed()).isEqualTo(2);
        assertThat(acknowledge.timesSkipped()).isEqualTo(1);
        RoleStepComplianceDto rule = roleStep(response, RoleStep.RULE);
        assertThat(rule.timesFollowed()).isEqualTo(1);
        assertThat(rule.timesSkipped()).isEqualTo(2);

        // Нарушение нормы: только let-through-friendly даёт отрицательную дельту безопасности
        // вместе с заполненным normRef.
        assertThat(response.frequentNormViolations()).hasSize(1);
        NormViolationDto violation = response.frequentNormViolations().get(0);
        assertThat(violation.normRef()).isEqualTo(letThroughFriendly.getNormRef());
        assertThat(violation.count()).isEqualTo(1);

        // Рекомендации: непройденные сценарии блока boarding (не медицины) + сам провальный
        // boarding-no-ticket на повтор.
        List<ScenarioRecommendationDto> recs = response.recommendations();
        assertThat(recs).extracting(ScenarioRecommendationDto::block).allMatch("boarding"::equals);
        assertThat(recs).extracting(ScenarioRecommendationDto::code)
                .contains("boarding-no-id", "boarding-late-passenger", "boarding-dead-phone", "boarding-no-ticket");
        ScenarioRecommendationDto retryBoardingNoTicket = recs.stream()
                .filter(r -> r.code().equals("boarding-no-ticket"))
                .findFirst().orElseThrow();
        assertThat(retryBoardingNoTicket.reason()).isEqualTo(RecommendationReason.FAILED);
        ScenarioRecommendationDto notPlayed = recs.stream()
                .filter(r -> r.code().equals("boarding-no-id"))
                .findFirst().orElseThrow();
        assertThat(notPlayed.reason()).isEqualTo(RecommendationReason.NOT_PLAYED);
    }

    @Test
    void analyzeForPlayerWithoutPlaythroughsReturnsEmptyStateNotError() {
        UUID playerId = UUID.randomUUID();

        CompetencyAnalyticsResponse response = competencyAnalyticsService.analyze(playerId);

        assertThat(response.playerId()).isEqualTo(playerId);
        assertThat(response.totalPlaythroughs()).isZero();
        assertThat(response.blockStats()).isEmpty();
        assertThat(response.weakCompetencies()).isEmpty();
        assertThat(response.frequentNormViolations()).isEmpty();
        assertThat(response.roleStepCompliance()).hasSize(4);
        assertThat(response.roleStepCompliance()).allMatch(s -> s.complianceRate() == 0);
        // Просевших компетенций нет (ещё ничего не пройдено) -> рекомендации это просто
        // непройденные сценарии по всему каталогу, начиная с первого по situationRefId.
        assertThat(response.recommendations()).isNotEmpty();
        assertThat(response.recommendations().get(0).code()).isEqualTo("boarding-no-ticket");
        assertThat(response.recommendations()).allMatch(r -> r.reason() == RecommendationReason.NOT_PLAYED);
    }

    private BlockCompetencyStatsDto blockStats(CompetencyAnalyticsResponse response, String block) {
        return response.blockStats().stream()
                .filter(s -> s.block().equals(block))
                .findFirst().orElseThrow();
    }

    private RoleStepComplianceDto roleStep(CompetencyAnalyticsResponse response, RoleStep step) {
        return response.roleStepCompliance().stream()
                .filter(s -> s.step() == step)
                .findFirst().orElseThrow();
    }

    private ScenarioNode nodeByCode(UUID scenarioId, String code) {
        return scenarioNodeRepository.findByScenarioIdAndCode(scenarioId, code).orElseThrow();
    }

    private ScenarioChoice choiceByCode(UUID nodeId, String code) {
        List<ScenarioChoice> choices = scenarioChoiceRepository.findByNodeIdOrderBySortOrder(nodeId);
        return choices.stream().filter(c -> c.getCode().equals(code)).findFirst().orElseThrow();
    }
}
