package ru.vsm.backend.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.scenario.domain.Scenario;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import ru.vsm.backend.scenario.seed.ScenarioSeedDto;
import ru.vsm.backend.scenario.seed.ScenarioSeedExporter;
import ru.vsm.backend.scenario.service.ScenarioGraphValidator;

/**
 * Валидатор целостности графа для ВСЕХ сценариев, загруженных сидом (флагманских и простых) —
 * тонкая обвязка вокруг переиспользуемых production-компонентов, которые использует и
 * {@code EditorScenarioController} (редактор сценариев): {@link ScenarioSeedExporter} превращает
 * персистентный граф (id-ссылки) обратно в {@link ScenarioSeedDto} (код-ссылки, тот же формат,
 * что у seed-файлов), а {@link ScenarioGraphValidator} проверяет его целостность — локальные
 * инварианты (entryNode/target/defaultChoice существуют, у нетерминального узла есть выборы,
 * у терминального — {@code terminalOutcome}) и глобальные (каждый узел достижим из
 * {@code entryNode}, есть хотя бы один достижимый терминальный узел). Единая логика проверки
 * что для "сценария из файла" (редактор), что для "сценария, уже осевшего в БД" (этот тест) —
 * поэтому сам тест теперь не содержит собственной логики обхода графа.
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
    private ScenarioSeedExporter scenarioSeedExporter;

    @Autowired
    private ScenarioGraphValidator scenarioGraphValidator;

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
            ScenarioSeedDto exported = scenarioSeedExporter.export(scenario);
            List<String> errors = scenarioGraphValidator.validate(exported);
            assertThat(errors)
                    .as("сценарий '%s': граф должен быть структурно валиден", scenario.getCode())
                    .isEmpty();
        }
    }
}
