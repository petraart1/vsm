package ru.vsm.backend.scenario.service.exception;

/** Запрошено действие (старт текущего сценария) над экзаменом, который уже COMPLETED. Маппится в HTTP 409. */
public class ExamAlreadyFinishedException extends RuntimeException {

    public ExamAlreadyFinishedException(String message) {
        super(message);
    }
}
