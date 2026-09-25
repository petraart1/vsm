package ru.vsm.backend.scenario.service.exception;

/** Запрошенный choiceId не принадлежит текущему узлу прохождения. Маппится в HTTP 400. */
public class ChoiceNotAvailableException extends RuntimeException {

    public ChoiceNotAvailableException(String message) {
        super(message);
    }
}
