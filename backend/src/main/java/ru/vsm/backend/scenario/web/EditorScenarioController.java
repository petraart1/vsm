package ru.vsm.backend.scenario.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.scenario.domain.Scenario;
import ru.vsm.backend.scenario.event.ScenarioPublishedEvent;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import ru.vsm.backend.scenario.seed.ScenarioSeedDto;
import ru.vsm.backend.scenario.seed.ScenarioSeedExporter;
import ru.vsm.backend.scenario.seed.ScenarioSeedService;
import ru.vsm.backend.scenario.seed.ScenarioSeedTemplateFactory;
import ru.vsm.backend.scenario.seed.markdown.ScenarioMarkdownParser;
import ru.vsm.backend.scenario.service.ScenarioGraphValidator;
import ru.vsm.backend.scenario.service.exception.MarkdownImportException;
import ru.vsm.backend.scenario.service.exception.ScenarioGraphInvalidException;
import ru.vsm.backend.scenario.service.exception.ScenarioNotFoundException;
import ru.vsm.backend.scenario.web.dto.GraphValidationResponse;
import ru.vsm.backend.scenario.web.dto.MarkdownImportResponse;
import ru.vsm.backend.scenario.web.dto.ScenarioSummaryResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Редактор сценариев: добавить новую ситуацию (или отредактировать существующую) без пересборки */
@RestController
@RequestMapping("/api/editor")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.editor.enabled", havingValue = "true", matchIfMissing = true)
public class EditorScenarioController {

    private final ScenarioGraphValidator scenarioGraphValidator;
    private final ScenarioSeedService scenarioSeedService;
    private final ScenarioSeedExporter scenarioSeedExporter;
    private final ScenarioRepository scenarioRepository;
    private final ScenarioMarkdownParser scenarioMarkdownParser;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

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
        publishIfNew(isNew, saved);
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

    /** Импорт ситуации из простого построчного markdown-формата (README, раздел «Импорт из */
    @PostMapping("/scenarios/import-markdown")
    public ResponseEntity<?> importMarkdown(
            @RequestBody String rawBody, @RequestParam(defaultValue = "false") boolean save) {
        String markdown = extractMarkdown(rawBody);
        ScenarioSeedDto dto = scenarioMarkdownParser.parse(markdown);
        List<String> graphErrors = scenarioGraphValidator.validate(dto);

        if (!save) {
            return ResponseEntity.ok(new MarkdownImportResponse(dto, graphErrors));
        }
        if (!graphErrors.isEmpty()) {
            throw new ScenarioGraphInvalidException(graphErrors);
        }
        boolean isNew = !scenarioRepository.existsByCode(dto.getCode());
        UUID id = scenarioSeedService.upsertForEditor(dto);
        Scenario saved = scenarioRepository.findById(id)
                .orElseThrow(() -> new ScenarioNotFoundException("Сценарий '" + dto.getCode() + "' не найден"));
        publishIfNew(isNew, saved);
        return ResponseEntity.status(isNew ? HttpStatus.CREATED : HttpStatus.OK).body(toSummary(saved));
    }

    /** Публикует {@link ScenarioPublishedEvent} только для только что созданного сценария */
    private void publishIfNew(boolean isNew, Scenario saved) {
        if (isNew) {
            eventPublisher.publishEvent(new ScenarioPublishedEvent(
                    saved.getId(), saved.getCode(), saved.getTitle(), saved.getBlock(), Instant.now()));
        }
    }

    /** Тело запроса — JSON {@code {"markdown": "..."}}, если начинается с {@code '{'}, иначе сырой markdown как есть. */
    private String extractMarkdown(String rawBody) {
        String trimmed = rawBody == null ? "" : rawBody.strip();
        if (!trimmed.startsWith("{")) {
            return rawBody;
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(trimmed);
        } catch (RuntimeException e) {
            throw new MarkdownImportException(List.of("тело запроса похоже на JSON, но не распарсилось: " + e.getMessage()));
        }
        JsonNode markdownNode = node.get("markdown");
        if (markdownNode == null || markdownNode.isNull()) {
            throw new MarkdownImportException(
                    List.of("тело запроса — JSON, но строковое поле 'markdown' отсутствует"));
        }
        return markdownNode.asString();
    }

    private ScenarioSummaryResponse toSummary(Scenario s) {
        return new ScenarioSummaryResponse(
                s.getId(), s.getCode(), s.getSituationRefId(), s.getBlock(), s.getTitle(), s.getDescription(),
                s.isFlagship());
    }
}
