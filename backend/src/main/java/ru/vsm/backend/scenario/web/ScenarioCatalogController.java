package ru.vsm.backend.scenario.web;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.scenario.domain.Scenario;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import ru.vsm.backend.scenario.service.exception.ScenarioNotFoundException;
import ru.vsm.backend.scenario.web.dto.ScenarioSummaryResponse;

/**
 * Каталог сценариев: список (с признаком блока/флагманский) и деталь одного сценария —
 * без раскрытия графа узлов (это уже {@code ScenarioPlayController}).
 */
@RestController
@RequestMapping("/api/scenarios")
@RequiredArgsConstructor
public class ScenarioCatalogController {

    private final ScenarioRepository scenarioRepository;

    @GetMapping
    public List<ScenarioSummaryResponse> list(@RequestParam(required = false) String block) {
        return scenarioRepository.findAll().stream()
                .filter(Scenario::isActive)
                .filter(s -> block == null || block.equals(s.getBlock()))
                .sorted(Comparator.comparing(
                        Scenario::getSituationRefId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toSummary)
                .toList();
    }

    @GetMapping("/{scenarioId}")
    public ScenarioSummaryResponse get(@PathVariable UUID scenarioId) {
        Scenario scenario = scenarioRepository.findById(scenarioId)
                .filter(Scenario::isActive)
                .orElseThrow(() -> new ScenarioNotFoundException("Сценарий '" + scenarioId + "' не найден"));
        return toSummary(scenario);
    }

    private ScenarioSummaryResponse toSummary(Scenario s) {
        return new ScenarioSummaryResponse(
                s.getId(), s.getCode(), s.getSituationRefId(), s.getBlock(), s.getTitle(), s.getDescription(),
                s.isFlagship());
    }
}
