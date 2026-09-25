package ru.vsm.backend.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.scenario.domain.Scenario;
import ru.vsm.backend.scenario.domain.ScenarioNode;
import ru.vsm.backend.scenario.repository.ScenarioChoiceRepository;
import ru.vsm.backend.scenario.repository.ScenarioNodeRepository;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import ru.vsm.backend.scenario.seed.ScenarioSeedDto;
import ru.vsm.backend.scenario.seed.ScenarioSeedService;

/**
 * Проверяет, что Liquibase-миграции домена scenario применяются и seed-файлы из
 * {@code classpath:scenarios/*.json} загружаются при старте контекста (через
 * {@code ScenarioSeedLoader}), а повторный запуск загрузчика для того же кода сценария
 * ничего не дублирует (идемпотентность).
 */
@SpringBootTest
@Testcontainers
class ScenarioSeedIntegrationTest {

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
    private ScenarioSeedService scenarioSeedService;

    @Test
    void boardingFlagshipScenarioIsSeededWithFullGraph() {
        Optional<Scenario> scenario = scenarioRepository.findByCode("boarding-no-ticket");
        assertThat(scenario).isPresent();
        assertThat(scenario.get().isFlagship()).isTrue();
        assertThat(scenario.get().getEntryNodeId()).isNotNull();

        List<ScenarioNode> nodes = scenarioNodeRepository.findByScenarioId(scenario.get().getId());
        assertThat(nodes).hasSize(11);
        assertThat(nodes).filteredOn(ScenarioNode::isTerminal).hasSize(6);

        long totalChoices = nodes.stream()
                .mapToLong(n -> scenarioChoiceRepository.findByNodeIdOrderBySortOrder(n.getId()).size())
                .sum();
        assertThat(totalChoices).isGreaterThan(0);
    }

    @Test
    void medicalFlagshipScenarioHasTimersAndDefaultChoiceOnEntryNode() {
        Optional<Scenario> scenario = scenarioRepository.findByCode("medical-passenger-unwell");
        assertThat(scenario).isPresent();
        assertThat(scenario.get().getBlock()).isEqualTo("medical");

        ScenarioNode entryNode = scenarioNodeRepository.findById(scenario.get().getEntryNodeId()).orElseThrow();
        assertThat(entryNode.getCode()).isEqualTo("start");
        assertThat(entryNode.getTimerSeconds()).isEqualTo(30);
        assertThat(entryNode.getDefaultChoiceId()).isNotNull();
    }

    @Test
    void reseedingExistingScenarioCodeIsSkippedAndDoesNotDuplicate() {
        long nodesBefore = scenarioRepository.findByCode("boarding-no-ticket")
                .map(s -> scenarioNodeRepository.findByScenarioId(s.getId()).size())
                .orElse(0);

        ScenarioSeedDto duplicate = new ScenarioSeedDto();
        duplicate.setCode("boarding-no-ticket");
        duplicate.setBlock("boarding");
        duplicate.setTitle("Дубликат для проверки идемпотентности");
        duplicate.setEntryNode("does-not-matter");
        // nodes умышленно пустой список — если бы сервис не пропускал существующий код,
        // он бы упал на валидации "нет узлов", а не тихо прошёл.
        scenarioSeedService.seed(duplicate);

        long nodesAfter = scenarioRepository.findByCode("boarding-no-ticket")
                .map(s -> scenarioNodeRepository.findByScenarioId(s.getId()).size())
                .orElse(0);
        assertThat(nodesAfter).isEqualTo(nodesBefore);
        assertThat(scenarioRepository.findAll())
                .filteredOn(s -> s.getCode().equals("boarding-no-ticket"))
                .hasSize(1);
    }
}
