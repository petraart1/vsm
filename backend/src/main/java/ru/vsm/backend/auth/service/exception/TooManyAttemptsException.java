package ru.vsm.backend.auth.service.exception;

/** Слишком много неудачных попыток входа подряд для одной пары логин+IP (см. */
public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException(String message) {
        super(message);
    }
}
