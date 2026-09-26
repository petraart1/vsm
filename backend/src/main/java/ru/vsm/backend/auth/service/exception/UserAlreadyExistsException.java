package ru.vsm.backend.auth.service.exception;

/** Регистрация с уже занятым логином или email. Маппится в HTTP 409. */
public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
