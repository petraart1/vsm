package ru.vsm.backend.auth.service.exception;

/** Логин с неверным логином или паролем. Маппится в HTTP 401. Сообщение намеренно не различает
 * "нет такого логина" и "неверный пароль" — не давать подсказку для перебора логинов. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
