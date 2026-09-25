package ru.vsm.backend.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.feedback.dto.DebriefResponse;
import ru.vsm.backend.feedback.dto.DebriefStepDto;
import ru.vsm.backend.feedback.service.DebriefService;
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
 * Проверяет разбор прохождения (debrief) на уже засеянном флагманском сценарии
 * {@code boarding-no-ticket} (грузится {@code ScenarioSeedLoader} при старте контекста).
 *
 * <p>История выборов вставляется в БД напрямую через репозитории (read-only использование),
 * имитируя уже завершённое прохождение.
 */
@SpringBootTest
@Testcontainers
class DebriefServiceIntegrationTest {

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
    private DebriefService debriefService;

    @Test
    void debriefForGoodBoardingRunBuildsTimelineWithRoleStepsAndKeyMoment() {
        Scenario scenario = scenarioRepository.findByCode("boarding-no-ticket").orElseThrow();

        ScenarioNode startNode = nodeByCode(scenario.getId(), "start");
        ScenarioChoice acknowledgeAndExplain = choiceByCode(startNode.getId(), "acknowledge-and-explain");
        ScenarioNode explainRuleNode = nodeByCode(scenario.getId(), "explain-rule");
        ScenarioChoice politeFirmClose = choiceByCode(explainRuleNode.getId(), "polite-firm-close");

        UserProgress progress = userProgressRepository.save(UserProgress.builder()
                .userId(UUID.randomUUID())
                .scenarioId(scenario.getId())
                .currentNodeId(null)
                .status(ProgressStatus.COMPLETED)
                .loyaltyScore(acknowledgeAndExplain.getLoyaltyDelta() + politeFirmClose.getLoyaltyDelta())
                .safetyScore(acknowledgeAndExplain.getSafetyDelta() + politeFirmClose.getSafetyDelta())
                .finalOutcome(ScenarioOutcome.SUCCESS)
                .build());

        historyRepository.save(ScenarioChoiceHistory.builder()
                .userProgressId(progress.getId())
                .nodeId(startNode.getId())
                .choiceId(acknowledgeAndExplain.getId())
                .sequenceIndex(0)
                .wasTimeout(false)
                .loyaltyDeltaApplied(acknowledgeAndExplain.getLoyaltyDelta())
                .safetyDeltaApplied(acknowledgeAndExplain.getSafetyDelta())
                .build());
        historyRepository.save(ScenarioChoiceHistory.builder()
                .userProgressId(progress.getId())
                .nodeId(explainRuleNode.getId())
                .choiceId(politeFirmClose.getId())
                .sequenceIndex(1)
                .wasTimeout(false)
                .loyaltyDeltaApplied(politeFirmClose.getLoyaltyDelta())
                .safetyDeltaApplied(politeFirmClose.getSafetyDelta())
                .build());

        DebriefResponse debrief = debriefService.buildDebrief(progress.getId());

        assertThat(debrief.scenarioCode()).isEqualTo("boarding-no-ticket");
        assertThat(debrief.progressStatus()).isEqualTo(ProgressStatus.COMPLETED);
        assertThat(debrief.outcome()).isEqualTo(ScenarioOutcome.SUCCESS);
        assertThat(debrief.verdict()).isEqualTo("Хорошо справились");
        assertThat(debrief.interrupted()).isFalse();
        assertThat(debrief.finalLoyaltyScore()).isEqualTo(progress.getLoyaltyScore());
        assertThat(debrief.finalSafetyScore()).isEqualTo(progress.getSafetyScore());

        assertThat(debrief.timeline()).hasSize(2);
        DebriefStepDto firstStep = debrief.timeline().get(0);
        assertThat(firstStep.nodeCode()).isEqualTo("start");
        assertThat(firstStep.choiceCode()).isEqualTo("acknowledge-and-explain");
        // acknowledge-and-explain: roleSteps {acknowledge, rule, solution} — reassure пропущен.
        assertThat(firstStep.roleStepsCompleted())
                .containsExactlyInAnyOrder("Признать ситуацию", "Обозначить правило", "Предложить решение");
        assertThat(firstStep.roleStepsSkipped()).containsExactly("Заверить");
        // loyaltyDelta -2, safetyDelta +5 — разные знаки => осознанный компромисс шкал.
        assertThat(firstStep.scaleConflict()).isTrue();
        assertThat(firstStep.explanation()).contains("компромисс");

        // На узле "start" лучшая альтернатива по сумме дельт — offer-to-check (score 5),
        // выбранный acknowledge-and-explain даёт score 3 => это и есть ключевая развилка.
        assertThat(debrief.keyMoment()).isNotNull();
        assertThat(debrief.keyMoment().sequenceIndex()).isEqualTo(0);
        assertThat(debrief.keyMoment().betterChoiceText())
                .isEqualTo(choiceByCode(startNode.getId(), "offer-to-check").getText());
    }

    @Test
    void debriefFlagsSafetyNormViolationAndTimeoutChoice() {
        Scenario scenario = scenarioRepository.findByCode("boarding-no-ticket").orElseThrow();
        ScenarioNode startNode = nodeByCode(scenario.getId(), "start");
        ScenarioChoice letThroughFriendly = choiceByCode(startNode.getId(), "let-through-friendly");

        UserProgress progress = userProgressRepository.save(UserProgress.builder()
                .userId(UUID.randomUUID())
                .scenarioId(scenario.getId())
                .currentNodeId(null)
                .status(ProgressStatus.COMPLETED)
                .loyaltyScore(letThroughFriendly.getLoyaltyDelta())
                .safetyScore(letThroughFriendly.getSafetyDelta())
                .finalOutcome(ScenarioOutcome.FAILURE)
                .build());

        historyRepository.save(ScenarioChoiceHistory.builder()
                .userProgressId(progress.getId())
                .nodeId(startNode.getId())
                .choiceId(letThroughFriendly.getId())
                .sequenceIndex(0)
                .wasTimeout(true)
                .loyaltyDeltaApplied(letThroughFriendly.getLoyaltyDelta())
                .safetyDeltaApplied(letThroughFriendly.getSafetyDelta())
                .build());

        DebriefResponse debrief = debriefService.buildDebrief(progress.getId());

        assertThat(debrief.verdict()).isEqualTo("Критическая ошибка безопасности");
        DebriefStepDto step = debrief.timeline().get(0);
        assertThat(step.wasTimeout()).isTrue();
        assertThat(step.explanation()).contains("Время на решение истекло");
        assertThat(step.explanation()).contains("dataset/standards/sto-rzd-03011-general.md");
        assertThat(debrief.normReferences()).containsExactly("dataset/standards/sto-rzd-03011-general.md");
    }

    private ScenarioNode nodeByCode(UUID scenarioId, String code) {
        return scenarioNodeRepository.findByScenarioIdAndCode(scenarioId, code).orElseThrow();
    }

    private ScenarioChoice choiceByCode(UUID nodeId, String code) {
        List<ScenarioChoice> choices = scenarioChoiceRepository.findByNodeIdOrderBySortOrder(nodeId);
        return choices.stream().filter(c -> c.getCode().equals(code)).findFirst().orElseThrow();
    }
}
