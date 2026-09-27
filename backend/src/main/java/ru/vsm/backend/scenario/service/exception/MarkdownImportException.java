package ru.vsm.backend.scenario.service.exception;

import java.util.List;
import lombok.Getter;

/** Markdown, присланный в {@code POST /api/editor/scenarios/import-markdown}, не удалось разобрать */
@Getter
public class MarkdownImportException extends RuntimeException {

    private final List<String> errors;

    public MarkdownImportException(List<String> errors) {
        super("Не удалось разобрать markdown сценария: " + errors.size() + " проблем(а)");
        this.errors = errors;
    }
}
