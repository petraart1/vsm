package ru.vsm.backend.scenario.web;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.scenario.domain.Scenario;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import ru.vsm.backend.scenario.seed.ScenarioSeedDto;
import ru.vsm.backend.scenario.seed.ScenarioSeedExporter;
import ru.vsm.backend.scenario.seed.ScenarioSeedService;
import ru.vsm.backend.scenario.seed.ScenarioSeedTemplateFactory;
import ru.vsm.backend.scenario.service.ScenarioGraphValidator;
import ru.vsm.backend.scenario.service.exception.ScenarioGraphInvalidException;
import ru.vsm.backend.scenario.service.exception.ScenarioNotFoundException;
import ru.vsm.backend.scenario.web.dto.GraphValidationResponse;
import ru.vsm.backend.scenario.web.dto.ScenarioSummaryResponse;

/**
 * Редактор сценариев: добавить новую ситуацию (или отредактировать существующую) без пересборки
 * приложения — тот же JSON-формат, что и seed-файлы в {@code classpath:scenarios/*.json}
 * ({@link ScenarioSeedDto}), принимается по REST и сразу доступен в каталоге
 * ({@code GET /api/scenarios}) и для прохождения ({@code ScenarioPlayController}).
 *
 * <p>Включается свойством {@code app.editor.enabled} (по умолчанию {@code true} — назначение
 * этого API демонстрационное: без авторизации, доступно всем, кто может достучаться до backend.
 * В продовом контуре редактор должен быть либо выключен ({@code app.editor.enabled=false}), либо
 * закрыт отдельным слоем авторизации — это не входит в MVP.
 *
 * <p>Правила версионирования обновления существующего сценария — см. javadoc
 * {@code ScenarioSeedService.upsertForEditor}: обновление графа сценария, по которому уже есть
 * прохождения, запрещено (409), чтобы не порвать историю уже пройденных игр.
 */
@RestController
@RequestMapping("/api/editor")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.editor.enabled", havingValue = "true", matchIfMissing = true)
public class EditorScenarioController {

    private final ScenarioGraphValidator scenarioGraphValidator;
    private final ScenarioSeedService scenarioSeedService;
    private final ScenarioSeedExporter scenarioSeedExporter;
    private final ScenarioRepository scenarioRepository;

    /** Проверка графа без сохранения — список проблем (узел/выбор/что не так), пуст = граф валиден. */
    @PostMapping("/scenarios/validate")
    public GraphValidationResponse validate(@RequestBody ScenarioSeedDto dto) {
        List<String> errors = scenarioGraphValidator.validate(dto);
        return new GraphValidationResponse(errors.isEmpty(), errors);
    }

    /**
     * Валидирует и сохраняет сценарий: новый {@code code} — создаёт (201), уже существующий —
     * обновляет граф (200), если по нему ещё не было прохождений (иначе 409, см. класс-javadoc).
     */
    @PostMapping("/scenarios")
    public ResponseEntity<ScenarioSummaryResponse> save(@RequestBody ScenarioSeedDto dto) {
        List<String> errors = scenarioGraphValidator.validate(dto);
        if (!errors.isEmpty()) {
            throw new ScenarioGraphInvalidException(errors);
        }
        boolean isNew = !scenarioRepository.existsByCode(dto.getCode());
        UUID id = scenarioSeedService.upsertForEditor(dto);
        Scenario saved = scenarioRepository.findById(id)
                .orElseThrow(() -> new ScenarioNotFoundException("Сценарий '" + dto.getCode() + "' не найден"));
        ScenarioSummaryResponse body = toSummary(saved);
        return ResponseEntity.status(isNew ? HttpStatus.CREATED : HttpStatus.OK).body(body);
    }

    /** Экспорт существующего сценария в том же JSON-формате, что и вход {@code POST /scenarios} — для правки. */
    @GetMapping("/scenarios/{code}")
    public ScenarioSeedDto export(@PathVariable String code) {
        return scenarioSeedExporter.export(code);
    }

    /** Шаблон простого сценария (вводный узел + 3 варианта) — отправная точка для новой ситуации. */
    @GetMapping("/template")
    public ScenarioSeedDto template() {
        return ScenarioSeedTemplateFactory.build();
    }

    private ScenarioSummaryResponse toSummary(Scenario s) {
        return new ScenarioSummaryResponse(
                s.getId(), s.getCode(), s.getSituationRefId(), s.getBlock(), s.getTitle(), s.getDescription(),
                s.isFlagship());
    }
}
