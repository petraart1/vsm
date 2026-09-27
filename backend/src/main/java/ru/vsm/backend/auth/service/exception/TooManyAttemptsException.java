package ru.vsm.backend.auth.service.exception;

/**
 * Слишком много неудачных попыток входа подряд для одной пары логин+IP (см.
 * {@code ru.vsm.backend.auth.security.LoginRateLimiter}) — HIGH из аудита безопасности,
 * "нет ограничения брутфорса логина". Код ошибки {@code too_many_attempts}, HTTP 429.
 */
public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException(String message) {
        super(message);
    }
}
