package ru.vsm.backend.scenario.service.exception;

/** Действие запрошено над прохождением, которое уже COMPLETED/ABANDONED. Маппится в HTTP 409. */
public class ProgressAlreadyCompletedException extends RuntimeException {

    public ProgressAlreadyCompletedException(String message) {
        super(message);
    }
}
