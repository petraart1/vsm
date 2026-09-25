package ru.vsm.backend.scenario.service.exception;

/** Сценарий с запрошенным id не найден или неактивен. Маппится в HTTP 404. */
public class ScenarioNotFoundException extends RuntimeException {

    public ScenarioNotFoundException(String message) {
        super(message);
    }
}
