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

    /**
     * Проверяет, что все известные на данный момент seed-файлы из {@code classpath:scenarios/*.json}
     * загружены при старте. Намеренно {@code contains}, а не {@code containsExactlyInAnyOrder}: несколько
     * агентов параллельно добавляют новые файлы ситуаций в {@code resources/scenarios/}, точное число
     * сценариев на любой момент времени не фиксировано (см. STATUS.md). Полное покрытие 51/51 проверяется
     * отдельным подсчётом файлов, а не перечислением кодов здесь.
     */
    @Test
    void allKnownSeedFilesAreLoadedAtStartup() {
        assertThat(scenarioRepository.findAll())
                .extracting(Scenario::getCode)
                .contains(
                        "boarding-no-ticket", "medical-passenger-unwell", "intoxicated-passenger",
                        "passengers-arguing",
                        "boarding-no-id", "boarding-late-passenger", "boarding-dead-phone",
                        "baggage-bicycle-unpacked", "baggage-aisle-blocked",
                        "safety-smoking", "safety-alcohol-outside-bistro", "safety-property-damage",
                        "catering-dish-unavailable",
                        "catering-service-is-paid", "catering-portion-size", "catering-alcohol-to-intoxicated",
                        "catering-peek-at-first-class",
                        "medical-medication-request", "medical-panic-attack", "medical-lost-child",
                        "medical-crying-child-complaints", "medical-general-panic",
                        "conflict-passenger-rude", "conflict-filming-without-consent",
                        "misc-delay-complaint", "misc-missing-service", "misc-baby-care-space",
                        "misc-slept-through-stop", "misc-wants-to-complain", "misc-watch-my-child");
    }

    @Test
    void intoxicatedPassengerFlagshipScenarioHasHiddenRadioFormulationMechanic() {
        Scenario scenario = scenarioRepository.findByCode("intoxicated-passenger").orElseThrow();
        assertThat(scenario.isFlagship()).isTrue();
        assertThat(scenario.getBlock()).isEqualTo("safety");

        ScenarioNode radioNode = scenarioNodeRepository
                .findByScenarioIdAndCode(scenario.getId(), "radio-call-chief")
                .orElseThrow();
        List<ru.vsm.backend.scenario.domain.ScenarioChoice> radioChoices =
                scenarioChoiceRepository.findByNodeIdOrderBySortOrder(radioNode.getId());
        assertThat(radioChoices).hasSize(2);

        ru.vsm.backend.scenario.domain.ScenarioChoice neutral = radioChoices.stream()
                .filter(c -> c.getCode().equals("neutral-formulation")).findFirst().orElseThrow();
        ru.vsm.backend.scenario.domain.ScenarioChoice open = radioChoices.stream()
                .filter(c -> c.getCode().equals("open-formulation")).findFirst().orElseThrow();

        // Пассажир узел "не слышит" — обе формулировки не влияют на лояльность,
        // но открытая формулировка штрафует безопасность, нейтральная — нет.
        assertThat(neutral.getLoyaltyDelta()).isZero();
        assertThat(open.getLoyaltyDelta()).isZero();
        assertThat(neutral.getSafetyDelta()).isPositive();
        assertThat(open.getSafetyDelta()).isNegative();
        assertThat(open.getExplanation()).isNotBlank();
    }

    @Test
    void passengersArguingConflictFlagshipScenarioIsSeeded() {
        Scenario scenario = scenarioRepository.findByCode("passengers-arguing").orElseThrow();
        assertThat(scenario.isFlagship()).isTrue();
        assertThat(scenario.getBlock()).isEqualTo("conflict");

        List<ScenarioNode> nodes = scenarioNodeRepository.findByScenarioId(scenario.getId());
        assertThat(nodes).hasSize(11);
        assertThat(nodes).filteredOn(ScenarioNode::isTerminal).hasSize(7);
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
