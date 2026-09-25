package ru.vsm.backend.scenario.service.exception;

/** Прохождение (user_progress) с запрошенным id не найдено. Маппится в HTTP 404. */
public class ProgressNotFoundException extends RuntimeException {

    public ProgressNotFoundException(String message) {
        super(message);
    }
}
