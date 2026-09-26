package ru.vsm.backend.scenario.service.exception;

import java.util.List;
import lombok.Getter;

/**
 * Markdown, присланный в {@code POST /api/editor/scenarios/import-markdown}, не удалось разобрать
 * в валидный {@code ScenarioSeedDto} — список проблем разметки (строка + причина) в
 * {@link #getErrors()}. Маппится в HTTP 400, тем же телом ответа, что и
 * {@link ScenarioGraphInvalidException} (список проблем, а не одна строка).
 *
 * <p>Это ошибки именно разметки (не удалось распознать структуру строки/обязательные метаданные) —
 * отдельно от ошибок графа ({@link ScenarioGraphInvalidException}, {@code ScenarioGraphValidator}),
 * которые проверяются уже после успешного разбора markdown.
 */
@Getter
public class MarkdownImportException extends RuntimeException {

    private final List<String> errors;

    public MarkdownImportException(List<String> errors) {
        super("Не удалось разобрать markdown сценария: " + errors.size() + " проблем(а)");
        this.errors = errors;
    }
}
