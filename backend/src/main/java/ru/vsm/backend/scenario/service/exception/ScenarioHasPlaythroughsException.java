package ru.vsm.backend.scenario.service.exception;

/** Редактор сценариев попытался обновить граф сценария, по которому уже есть хотя бы одно */
public class ScenarioHasPlaythroughsException extends RuntimeException {

    public ScenarioHasPlaythroughsException(String message) {
        super(message);
    }
}
