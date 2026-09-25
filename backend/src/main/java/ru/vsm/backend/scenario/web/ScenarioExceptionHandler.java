package ru.vsm.backend.scenario.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.vsm.backend.scenario.service.exception.ChoiceNotAvailableException;
import ru.vsm.backend.scenario.service.exception.NoActiveTimerException;
import ru.vsm.backend.scenario.service.exception.ProgressAccessDeniedException;
import ru.vsm.backend.scenario.service.exception.ProgressAlreadyCompletedException;
import ru.vsm.backend.scenario.service.exception.ProgressNotFoundException;
import ru.vsm.backend.scenario.service.exception.ScenarioNotFoundException;
import ru.vsm.backend.scenario.web.dto.ErrorResponse;

/** Маппинг исключений домена scenario (REST прохождения) в HTTP-ответы с единым телом ошибки. */
@RestControllerAdvice(basePackages = "ru.vsm.backend.scenario.web")
public class ScenarioExceptionHandler {

    @ExceptionHandler(ScenarioNotFoundException.class)
    public ResponseEntity<ErrorResponse> handle(ScenarioNotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "scenario_not_found", e);
    }

    @ExceptionHandler(ProgressNotFoundException.class)
    public ResponseEntity<ErrorResponse> handle(ProgressNotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "progress_not_found", e);
    }

    @ExceptionHandler(ChoiceNotAvailableException.class)
    public ResponseEntity<ErrorResponse> handle(ChoiceNotAvailableException e) {
        return respond(HttpStatus.BAD_REQUEST, "choice_not_available", e);
    }

    @ExceptionHandler(NoActiveTimerException.class)
    public ResponseEntity<ErrorResponse> handle(NoActiveTimerException e) {
        return respond(HttpStatus.BAD_REQUEST, "no_active_timer", e);
    }

    @ExceptionHandler(ProgressAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handle(ProgressAccessDeniedException e) {
        return respond(HttpStatus.FORBIDDEN, "progress_access_denied", e);
    }

    @ExceptionHandler(ProgressAlreadyCompletedException.class)
    public ResponseEntity<ErrorResponse> handle(ProgressAlreadyCompletedException e) {
        return respond(HttpStatus.CONFLICT, "progress_already_completed", e);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handle(IllegalArgumentException e) {
        return respond(HttpStatus.BAD_REQUEST, "invalid_argument", e);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handle(MissingRequestHeaderException e) {
        return respond(HttpStatus.BAD_REQUEST, "missing_header", e);
    }

    /**
     * Defense-in-depth: основная защита от гонки двойного клика — пессимистичная блокировка
     * {@code UserProgress} в {@code ScenarioPlayService} (см. {@code UserProgressRepository.findByIdForUpdate}),
     * но если конкурентная запись всё же пробьёт {@code uq_choice_history_progress_sequence}
     * (миграция {@code scenario/010}), это конфликт состояния, а не внутренняя ошибка сервера.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handle(DataIntegrityViolationException e) {
        return respond(HttpStatus.CONFLICT, "concurrent_modification", e);
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String code, Exception e) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, e.getMessage()));
    }
}
