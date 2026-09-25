package ru.vsm.backend.scenario.service.exception;

import java.util.List;
import lombok.Getter;

/**
 * Граф сценария, присланный редактором, не прошёл {@code ScenarioGraphValidator} — список
 * проблем в {@link #getErrors()} (узел/выбор/что не так). Маппится в HTTP 400.
 */
@Getter
public class ScenarioGraphInvalidException extends RuntimeException {

    private final List<String> errors;

    public ScenarioGraphInvalidException(List<String> errors) {
        super("Граф сценария невалиден: " + errors.size() + " проблем(а)");
        this.errors = errors;
    }
}
