package ru.vsm.backend.feedback;

import static org.assertj.core.api.Assertions.assertThat;

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
import ru.vsm.backend.scenario.domain.NodeType;
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
import ru.vsm.backend.scenario.seed.ChoiceSeedDto;
import ru.vsm.backend.scenario.seed.NodeSeedDto;
import ru.vsm.backend.scenario.seed.RoleStepsSeedDto;
import ru.vsm.backend.scenario.seed.ScenarioSeedDto;
import ru.vsm.backend.scenario.seed.ScenarioSeedService;

/**
 * Проверяет, что {@code DebriefService} распознаёт закадровую коммуникацию по явному флагу узла
 * ({@code ScenarioNode.hiddenFromPassenger}), а не только по резервной эвристике (одинаковая
 * дельта лояльности у всех альтернатив узла-эскалации). Сценарий здесь — узел типа
 * {@code DIALOGUE} (не {@code ESCALATION}) с альтернативами, у которых лояльность различается —
 * эвристика в одиночку дала бы {@code false}, но флаг должен перекрыть её.
 */
@SpringBootTest
@Testcontainers
class DebriefHiddenFromPassengerFlagIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private ScenarioSeedService scenarioSeedService;
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
    void hiddenFromPassengerFlagMarksStepEvenWhenHeuristicWouldNot() {
        String code = "test-hidden-flag-" + UUID.randomUUID().toString().substring(0, 8);
        scenarioSeedService.seed(buildDto(code));

        Scenario scenario = scenarioRepository.findByCode(code).orElseThrow();
        ScenarioNode startNode = scenarioNodeRepository.findByScenarioIdAndCode(scenario.getId(), "start")
                .orElseThrow();
        ScenarioChoice goChoice = scenarioChoiceRepository.findByNodeIdOrderBySortOrder(startNode.getId())
                .stream().filter(c -> c.getCode().equals("go")).findFirst().orElseThrow();

        UserProgress progress = userProgressRepository.save(UserProgress.builder()
                .userId(UUID.randomUUID())
                .scenarioId(scenario.getId())
                .currentNodeId(null)
                .status(ProgressStatus.COMPLETED)
                .loyaltyScore(goChoice.getLoyaltyDelta())
                .safetyScore(goChoice.getSafetyDelta())
                .finalOutcome(ScenarioOutcome.SUCCESS)
                .build());
        historyRepository.save(ScenarioChoiceHistory.builder()
                .userProgressId(progress.getId())
                .nodeId(startNode.getId())
                .choiceId(goChoice.getId())
                .sequenceIndex(0)
                .wasTimeout(false)
                .loyaltyDeltaApplied(goChoice.getLoyaltyDelta())
                .safetyDeltaApplied(goChoice.getSafetyDelta())
                .build());

        assertThat(startNode.isHiddenFromPassenger()).isTrue();
        assertThat(startNode.getNodeType()).isEqualTo(NodeType.DIALOGUE);

        DebriefResponse debrief = debriefService.buildDebrief(progress.getId());
        DebriefStepDto step = debrief.timeline().get(0);
        assertThat(step.hiddenCommunicationEffect()).isTrue();
    }

    private ScenarioSeedDto buildDto(String code) {
        ScenarioSeedDto dto = new ScenarioSeedDto();
        dto.setCode(code);
        dto.setBlock("misc");
        dto.setTitle("Тестовый закадровый узел");
        dto.setFlagship(false);
        dto.setVersion(1);
        dto.setEntryNode("start");

        NodeSeedDto start = new NodeSeedDto();
        start.setCode("start");
        start.setType(NodeType.DIALOGUE.name());
        start.setText("Служебная ситуация, которую пассажир не наблюдает.");
        start.setHiddenFromPassenger(true);
        start.getChoices().add(choice("go", "Один вариант.", 5, 2, "end"));
        start.getChoices().add(choice("stay", "Другой вариант с иной лояльностью.", -3, 4, "end"));
        dto.getNodes().add(start);

        NodeSeedDto end = new NodeSeedDto();
        end.setCode("end");
        end.setType(NodeType.TERMINAL.name());
        end.setText("Итог.");
        end.setTerminal(true);
        end.setTerminalOutcome(ScenarioOutcome.SUCCESS.name());
        end.setOutcomeSummary("Итог.");
        dto.getNodes().add(end);

        return dto;
    }

    private ChoiceSeedDto choice(String code, String text, int loyaltyDelta, int safetyDelta, String target) {
        ChoiceSeedDto c = new ChoiceSeedDto();
        c.setCode(code);
        c.setText(text);
        c.setLoyaltyDelta(loyaltyDelta);
        c.setSafetyDelta(safetyDelta);
        c.setTarget(target);
        c.setRoleSteps(new RoleStepsSeedDto());
        return c;
    }
}
