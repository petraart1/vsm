package ru.vsm.backend.auth.esia.service.exception;

/** {@code code} из {@code /api/auth/esia/callback} неизвестен, уже использован или просрочен. Маппится в HTTP 400. */
public class InvalidEsiaCodeException extends RuntimeException {

    public InvalidEsiaCodeException(String message) {
        super(message);
    }
}
