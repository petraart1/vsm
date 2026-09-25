package ru.vsm.backend.scenario.service.exception;

/** Явный запрос "время вышло" для узла без таймера/defaultChoice. Маппится в HTTP 400. */
public class NoActiveTimerException extends RuntimeException {

    public NoActiveTimerException(String message) {
        super(message);
    }
}
