package ru.vsm.backend.scenario;

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
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.domain.Scenario;
import ru.vsm.backend.scenario.domain.ScenarioNode;
import ru.vsm.backend.scenario.domain.UserProgress;
import ru.vsm.backend.scenario.repository.ScenarioNodeRepository;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import ru.vsm.backend.scenario.repository.UserProgressRepository;
import ru.vsm.backend.scenario.seed.NodeSeedDto;
import ru.vsm.backend.scenario.seed.ScenarioSeedDto;
import ru.vsm.backend.scenario.seed.ScenarioSeedService;

/**
 * Проверяет version-aware обновление контента сценария в {@code ScenarioSeedService.seed}
 * (см. javadoc класса): более новая версия в файле перезаписывает граф на месте, если по
 * сценарию ещё нет ни одного {@code UserProgress}, и безопасно пропускается, если прохождение
 * уже есть — вместо того чтобы навсегда игнорировать контент после первого code-based skip.
 *
 * <p>Отдельный класс (свой контейнер) от {@link ScenarioSeedIntegrationTest} специально: оба
 * теста здесь мутируют граф уже засеянных при старте контекста флагманских сценариев
 * ({@code boarding-no-ticket}/{@code medical-passenger-unwell}), и это не должно влиять на
 * другие тестовые классы, которые полагаются на их исходную структуру.
 */
@SpringBootTest
@Testcontainers
class ScenarioSeedVersionUpdateIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private ScenarioRepository scenarioRepository;

    @Autowired
    private ScenarioNodeRepository scenarioNodeRepository;

    @Autowired
    private UserProgressRepository userProgressRepository;

    @Autowired
    private ScenarioSeedService scenarioSeedService;

    @Test
    void newerVersionReplacesGraphInPlaceWhenNoProgressExists() {
        Scenario before = scenarioRepository.findByCode("boarding-no-ticket").orElseThrow();
        assertThat(before.getVersion()).isEqualTo(2);
        UUID scenarioId = before.getId();
        assertThat(userProgressRepository.existsByScenarioId(scenarioId)).isFalse();

        ScenarioSeedDto v3 = minimalSingleTerminalNodeDto("boarding-no-ticket", "boarding", 3);
        scenarioSeedService.seed(v3);

        Scenario after = scenarioRepository.findByCode("boarding-no-ticket").orElseThrow();
        assertThat(after.getId()).isEqualTo(scenarioId);
        assertThat(after.getVersion()).isEqualTo(3);

        List<ScenarioNode> nodes = scenarioNodeRepository.findByScenarioId(scenarioId);
        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).getCode()).isEqualTo("only");
        assertThat(scenarioNodeRepository.findByScenarioIdAndCode(scenarioId, "start")).isEmpty();
    }

    @Test
    void newerVersionIsSkippedWhenScenarioAlreadyHasProgress() {
        Scenario before = scenarioRepository.findByCode("medical-passenger-unwell").orElseThrow();
        assertThat(before.getVersion()).isEqualTo(2);
        UUID scenarioId = before.getId();
        int nodesBefore = scenarioNodeRepository.findByScenarioId(scenarioId).size();

        Instant now = Instant.now();
        userProgressRepository.save(UserProgress.builder()
                .userId(UUID.randomUUID())
                .scenarioId(scenarioId)
                .currentNodeId(null)
                .status(ProgressStatus.COMPLETED)
                .loyaltyScore(10)
                .safetyScore(10)
                .startedAt(now)
                .updatedAt(now)
                .completedAt(now)
                .build());

        ScenarioSeedDto v3 = minimalSingleTerminalNodeDto("medical-passenger-unwell", "medical", 3);
        scenarioSeedService.seed(v3);

        Scenario after = scenarioRepository.findByCode("medical-passenger-unwell").orElseThrow();
        assertThat(after.getVersion()).isEqualTo(2);
        assertThat(scenarioNodeRepository.findByScenarioId(scenarioId)).hasSize(nodesBefore);
    }

    private ScenarioSeedDto minimalSingleTerminalNodeDto(String code, String block, int version) {
        ScenarioSeedDto dto = new ScenarioSeedDto();
        dto.setCode(code);
        dto.setBlock(block);
        dto.setTitle("Тестовая версия " + version);
        dto.setVersion(version);
        dto.setEntryNode("only");

        NodeSeedDto node = new NodeSeedDto();
        node.setCode("only");
        node.setType("TERMINAL");
        node.setText("Единственный узел версии " + version);
        node.setTerminal(true);
        node.setTerminalOutcome("SUCCESS");
        node.setOutcomeSummary("Минимальный граф для проверки обновления версии.");
        dto.setNodes(List.of(node));

        return dto;
    }
}
