package ru.vsm.backend.scenario.service.exception;

/** Экзамен с запрошенным id не найден. Маппится в HTTP 404. */
public class ExamNotFoundException extends RuntimeException {

    public ExamNotFoundException(String message) {
        super(message);
    }
}
