package ru.vsm.backend.scenario.service.exception;

/**
 * Редактор сценариев попытался обновить граф сценария, по которому уже есть хотя бы одно
 * прохождение ({@code user_progress}) — перезапись узлов/выборов порвала бы FK из
 * {@code scenario_choice_history} и/или прервала бы уже идущие прохождения. Маппится в HTTP 409.
 */
public class ScenarioHasPlaythroughsException extends RuntimeException {

    public ScenarioHasPlaythroughsException(String message) {
        super(message);
    }
}
