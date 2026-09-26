package ru.vsm.backend.auth.service.exception;

/** {@code GET /api/auth/me} без токена, с невалидным/просроченным токеном, или на удалённую учётку.
 * Маппится в HTTP 401. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
