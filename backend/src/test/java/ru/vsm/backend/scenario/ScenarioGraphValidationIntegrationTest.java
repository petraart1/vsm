package ru.vsm.backend.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.scenario.domain.Scenario;
import ru.vsm.backend.scenario.domain.ScenarioChoice;
import ru.vsm.backend.scenario.domain.ScenarioNode;
import ru.vsm.backend.scenario.repository.ScenarioChoiceRepository;
import ru.vsm.backend.scenario.repository.ScenarioNodeRepository;
import ru.vsm.backend.scenario.repository.ScenarioRepository;

/**
 * Валидатор целостности графа для ВСЕХ сценариев, загруженных сидом (флагманских и простых).
 *
 * <p>{@code ScenarioSeedService} уже проверяет на загрузке "локальные" инварианты (entryNode
 * существует, target каждого выбора существует среди узлов сценария, defaultChoice существует
 * среди выборов своего узла, нетерминальный узел не может быть без выборов) — падение сборки
 * при их нарушении. Этот тест проверяет "глобальные" инварианты графа, которые
 * {@code ScenarioSeedService} сознательно не проверяет (не мешает загрузке, но ломает
 * прохождение сценария в рантайме):
 * <ul>
 *   <li>каждый узел сценария достижим из {@code entryNode} по рёбрам "выбор → target"
 *       (недостижимый узел — мёртвый контент, на который никогда не попадёт игрок);</li>
 *   <li>хотя бы один терминальный узел существует и достижим (иначе прохождение сценария
 *       не может завершиться);</li>
 *   <li>у терминального узла нет собственных выборов, а у нетерминального — есть хотя бы
 *       один (дублирует проверку сидера как защиту от регресса, если сидер изменится);</li>
 *   <li>каждый терминальный узел имеет {@code terminalOutcome};</li>
 *   <li>у узла с {@code timerSeconds} обязательно задан {@code defaultChoiceId} — иначе
 *       по протоколу {@code ScenarioPlayService} таймер некому применить при истечении
 *       (см. javadoc {@code ScenarioPlayService}, fail-open на этот случай — тревожный
 *       сигнал для контента, а не штатный путь);</li>
 *   <li>{@code target_node_id} каждого выбора (если задан) указывает на узел ТОГО ЖЕ
 *       сценария — переход между графами разных сценариев невозможен по дизайну.</li>
 * </ul>
 *
 * <p>Проверяются ВСЕ сценарии, которые есть в БД на момент теста (полный набор
 * {@code classpath:scenarios/*.json}, независимо от того, флагманский сценарий или простой,
 * какой ролью написан) — новый seed-файл автоматически подхватывается без правки этого теста.
 */
@SpringBootTest
@Testcontainers
class ScenarioGraphValidationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private ScenarioRepository scenarioRepository;

    @Autowired
    private ScenarioNodeRepository scenarioNodeRepository;

    @Autowired
    private ScenarioChoiceRepository scenarioChoiceRepository;

    @Test
    void atLeastEightFlagshipScenariosAreSeeded() {
        List<Scenario> flagships = scenarioRepository.findAll().stream()
                .filter(Scenario::isFlagship)
                .toList();
        assertThat(flagships).hasSizeGreaterThanOrEqualTo(8);
    }

    @Test
    void everyScenarioGraphIsStructurallyValid() {
        List<Scenario> scenarios = scenarioRepository.findAll();
        assertThat(scenarios).isNotEmpty();

        for (Scenario scenario : scenarios) {
            validateGraph(scenario);
        }
    }

    private void validateGraph(Scenario scenario) {
        String label = scenario.getCode();
        assertThat(scenario.getEntryNodeId())
                .as("сценарий '%s': entryNodeId не должен быть null", label)
                .isNotNull();

        List<ScenarioNode> nodes = scenarioNodeRepository.findByScenarioId(scenario.getId());
        assertThat(nodes).as("сценарий '%s': должен содержать хотя бы один узел", label).isNotEmpty();

        Map<UUID, ScenarioNode> nodesById = new HashMap<>();
        for (ScenarioNode n : nodes) {
            nodesById.put(n.getId(), n);
        }
        assertThat(nodesById)
                .as("сценарий '%s': entryNodeId должен указывать на узел этого же сценария", label)
                .containsKey(scenario.getEntryNodeId());

        Map<UUID, List<ScenarioChoice>> choicesByNode = new HashMap<>();
        for (ScenarioNode n : nodes) {
            choicesByNode.put(n.getId(), scenarioChoiceRepository.findByNodeIdOrderBySortOrder(n.getId()));
        }

        // Локальные инварианты по каждому узлу.
        for (ScenarioNode n : nodes) {
            List<ScenarioChoice> choices = choicesByNode.get(n.getId());
            if (n.isTerminal()) {
                assertThat(choices)
                        .as("сценарий '%s', терминальный узел '%s': не должен иметь выборов",
                                label, n.getCode())
                        .isEmpty();
                assertThat(n.getTerminalOutcome())
                        .as("сценарий '%s', терминальный узел '%s': terminalOutcome обязателен",
                                label, n.getCode())
                        .isNotNull();
            } else {
                assertThat(choices)
                        .as("сценарий '%s', нетерминальный узел '%s': должен иметь хотя бы один выбор",
                                label, n.getCode())
                        .isNotEmpty();
            }
            if (n.getTimerSeconds() != null) {
                assertThat(n.getDefaultChoiceId())
                        .as("сценарий '%s', узел '%s': задан timerSeconds, но нет defaultChoiceId",
                                label, n.getCode())
                        .isNotNull();
                boolean defaultChoiceBelongsToNode = choices.stream()
                        .anyMatch(c -> c.getId().equals(n.getDefaultChoiceId()));
                assertThat(defaultChoiceBelongsToNode)
                        .as("сценарий '%s', узел '%s': defaultChoiceId должен быть одним из выборов этого узла",
                                label, n.getCode())
                        .isTrue();
            }
            for (ScenarioChoice c : choices) {
                if (c.getTargetNodeId() != null) {
                    assertThat(nodesById)
                            .as("сценарий '%s', выбор '%s.%s': target '%s' не найден среди узлов ЭТОГО сценария "
                                            + "(висячая ссылка или переход в чужой граф)",
                                    label, n.getCode(), c.getCode(), c.getTargetNodeId())
                            .containsKey(c.getTargetNodeId());
                }
            }
        }

        // Достижимость: обход из entryNode по рёбрам "выбор -> target".
        Set<UUID> reachable = new HashSet<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        queue.add(scenario.getEntryNodeId());
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (!reachable.add(current)) {
                continue;
            }
            List<ScenarioChoice> choices = choicesByNode.get(current);
            if (choices == null) {
                continue;
            }
            for (ScenarioChoice c : choices) {
                if (c.getTargetNodeId() != null) {
                    queue.add(c.getTargetNodeId());
                }
            }
        }

        Set<String> unreachableCodes = nodes.stream()
                .filter(n -> !reachable.contains(n.getId()))
                .map(ScenarioNode::getCode)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        if (!unreachableCodes.isEmpty()) {
            fail("сценарий '%s': узлы недостижимы из entryNode: %s".formatted(label, unreachableCodes));
        }

        boolean hasReachableTerminal = nodes.stream()
                .filter(n -> reachable.contains(n.getId()))
                .anyMatch(ScenarioNode::isTerminal);
        assertThat(hasReachableTerminal)
                .as("сценарий '%s': должен быть хотя бы один достижимый терминальный узел", label)
                .isTrue();
    }
}
