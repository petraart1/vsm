package ru.vsm.backend.scenario.web.dto;

import java.util.List;
import ru.vsm.backend.scenario.seed.ScenarioSeedDto;

/** Результат {@code POST /api/editor/scenarios/import-markdown} без сохранения ({@code save=false}, */
public record MarkdownImportResponse(ScenarioSeedDto scenario, List<String> errors) {
}
