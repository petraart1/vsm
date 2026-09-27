package ru.vsm.backend.scenario.web;

import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.vsm.backend.scenario.domain.Scenario;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import ru.vsm.backend.scenario.web.dto.AdminScenarioPatchRequest;
import ru.vsm.backend.scenario.web.dto.AdminScenarioSummaryResponse;

/**
 * Административный список сценариев (включая неактивные) и переключатель доступности. Путь под
 * {@code /api/admin/**}, доступен только роли {@code ADMIN} (см. {@code SecurityConfig}).
 *
 * <p>В отличие от {@link ScenarioCatalogController#list}, здесь возвращаются все сценарии
 * независимо от {@code active} — иначе администратор не смог бы снова включить уже выключенный
 * сценарий. Выключенный сценарий по-прежнему не отдаётся {@code GET /api/scenarios} и недоступен
 * для начала нового прохождения (см. {@code ScenarioPlayController}), но уже начатые прохождения
 * не прерывает.
 */
@RestController
@RequestMapping("/api/admin/scenarios")
@RequiredArgsConstructor
public class AdminScenarioController {

    private final ScenarioRepository scenarioRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public List<AdminScenarioSummaryResponse> list() {
        return scenarioRepository.findAll().stream()
                .sorted(Comparator.comparing(
                        Scenario::getSituationRefId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(AdminScenarioSummaryResponse::from)
                .toList();
    }

    @PatchMapping("/{code}")
    @Transactional
    public AdminScenarioSummaryResponse patch(@PathVariable String code, @RequestBody AdminScenarioPatchRequest request) {
        Scenario scenario = scenarioRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сценарий '" + code + "' не найден"));
        if (request.active() != null) {
            scenario.setActive(request.active());
        }
        return AdminScenarioSummaryResponse.from(scenarioRepository.save(scenario));
    }
}
