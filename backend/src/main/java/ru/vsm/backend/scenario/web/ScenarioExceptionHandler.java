package ru.vsm.backend.scenario.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.vsm.backend.config.error.ApiError;
import ru.vsm.backend.scenario.service.exception.ChoiceNotAvailableException;
import ru.vsm.backend.scenario.service.exception.ExamAlreadyFinishedException;
import ru.vsm.backend.scenario.service.exception.ExamNotFoundException;
import ru.vsm.backend.scenario.service.exception.MarkdownImportException;
import ru.vsm.backend.scenario.service.exception.NoActiveTimerException;
import ru.vsm.backend.scenario.service.exception.ProgressAccessDeniedException;
import ru.vsm.backend.scenario.service.exception.ProgressAlreadyCompletedException;
import ru.vsm.backend.scenario.service.exception.ProgressNotFoundException;
import ru.vsm.backend.scenario.service.exception.ScenarioGraphInvalidException;
import ru.vsm.backend.scenario.service.exception.ScenarioHasPlaythroughsException;
import ru.vsm.backend.scenario.service.exception.ScenarioNotFoundException;

/**
 * Маппинг исключений домена scenario (REST прохождения и редактора) в HTTP-ответы с единой формой
 * {@link ApiError}. {@code @Order(10)} — см. javadoc {@code AuthExceptionHandler} про приоритет
 * относительно {@code ru.vsm.backend.config.error.GlobalExceptionHandler}.
 */
@RestControllerAdvice(basePackages = "ru.vsm.backend.scenario.web")
@Order(10)
public class ScenarioExceptionHandler {

    @ExceptionHandler(ScenarioNotFoundException.class)
    public ResponseEntity<ApiError> handle(ScenarioNotFoundException e, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "scenario_not_found", e, request);
    }

    @ExceptionHandler(ProgressNotFoundException.class)
    public ResponseEntity<ApiError> handle(ProgressNotFoundException e, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "progress_not_found", e, request);
    }

    @ExceptionHandler(ChoiceNotAvailableException.class)
    public ResponseEntity<ApiError> handle(ChoiceNotAvailableException e, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "choice_not_available", e, request);
    }

    @ExceptionHandler(NoActiveTimerException.class)
    public ResponseEntity<ApiError> handle(NoActiveTimerException e, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "no_active_timer", e, request);
    }

    @ExceptionHandler(ProgressAccessDeniedException.class)
    public ResponseEntity<ApiError> handle(ProgressAccessDeniedException e, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, "progress_access_denied", e, request);
    }

    @ExceptionHandler(ProgressAlreadyCompletedException.class)
    public ResponseEntity<ApiError> handle(ProgressAlreadyCompletedException e, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "progress_already_completed", e, request);
    }

    @ExceptionHandler(ExamNotFoundException.class)
    public ResponseEntity<ApiError> handle(ExamNotFoundException e, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "exam_not_found", e, request);
    }

    @ExceptionHandler(ExamAlreadyFinishedException.class)
    public ResponseEntity<ApiError> handle(ExamAlreadyFinishedException e, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "exam_already_finished", e, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handle(IllegalArgumentException e, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "invalid_argument", e, request);
    }

    /** Редактор сценариев: граф не прошёл {@code ScenarioGraphValidator} — список проблем в {@code details}. */
    @ExceptionHandler(ScenarioGraphInvalidException.class)
    public ResponseEntity<ApiError> handle(ScenarioGraphInvalidException e, HttpServletRequest request) {
        return respondWithDetails(HttpStatus.BAD_REQUEST, "invalid_graph",
                "Граф сценария не прошёл проверку", e.getErrors(), request);
    }

    /** Редактор сценариев: импорт markdown — строка разметки не распознана/обязательные метаданные отсутствуют. */
    @ExceptionHandler(MarkdownImportException.class)
    public ResponseEntity<ApiError> handle(MarkdownImportException e, HttpServletRequest request) {
        return respondWithDetails(HttpStatus.BAD_REQUEST, "invalid_markdown",
                "Ошибки разметки markdown", e.getErrors(), request);
    }

    /** Редактор сценариев: обновление графа сценария, по которому уже есть прохождения. */
    @ExceptionHandler(ScenarioHasPlaythroughsException.class)
    public ResponseEntity<ApiError> handle(ScenarioHasPlaythroughsException e, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "scenario_has_playthroughs", e, request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handle(MissingRequestHeaderException e, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "missing_header", e, request);
    }

    /**
     * Defense-in-depth: основная защита от гонки двойного клика — пессимистичная блокировка
     * {@code UserProgress} в {@code ScenarioPlayService} (см. {@code UserProgressRepository.findByIdForUpdate}),
     * но если конкурентная запись всё же пробьёт {@code uq_choice_history_progress_sequence}
     * (миграция {@code scenario/010}), это конфликт состояния, а не внутренняя ошибка сервера.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handle(DataIntegrityViolationException e, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "concurrent_modification", e, request);
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, String code, Exception e, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(code, e.getMessage(), request.getRequestURI()));
    }

    private ResponseEntity<ApiError> respondWithDetails(
            HttpStatus status, String code, String message, List<String> details, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ApiError(code, message, details, request.getRequestURI()));
    }
}
