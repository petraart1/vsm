package ru.vsm.backend.scenario.web.dto;

import java.util.List;
import ru.vsm.backend.scenario.seed.ScenarioSeedDto;

/**
 * Результат {@code POST /api/editor/scenarios/import-markdown} без сохранения ({@code save=false},
 * по умолчанию): разобранный граф в обычном seed-формате плюс ошибки {@code ScenarioGraphValidator}
 * (пусто — граф валиден и готов к сохранению тем же {@code POST /api/editor/scenarios} или повторным
 * вызовом импорта с {@code save=true}). Ошибки самой markdown-разметки в этот ответ не попадают —
 * они прерывают разбор {@code 400 invalid_markdown} раньше, см. {@code ScenarioMarkdownParser}.
 */
public record MarkdownImportResponse(ScenarioSeedDto scenario, List<String> errors) {
}
