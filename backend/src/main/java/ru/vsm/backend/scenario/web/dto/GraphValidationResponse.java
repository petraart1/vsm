package ru.vsm.backend.scenario.web.dto;

import java.util.List;

/** Результат проверки графа сценария редактором, без сохранения — см. {@code /api/editor/scenarios/validate}. */
public record GraphValidationResponse(boolean valid, List<String> errors) {
}
