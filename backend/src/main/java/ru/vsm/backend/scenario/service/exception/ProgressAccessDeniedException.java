package ru.vsm.backend.scenario.service.exception;

/** Заголовок X-Player-Id не совпадает с владельцем прохождения. Маппится в HTTP 403. */
public class ProgressAccessDeniedException extends RuntimeException {

    public ProgressAccessDeniedException(String message) {
        super(message);
    }
}
