package ru.vsm.backend.config.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Обработчик ошибок framework-уровня (валидация, невалидные параметры, неизвестный
 * путь/метод, необработанные исключения) — единственный источник формы {@link ApiError} для
 * всего, что не покрыто доменными {@code @RestControllerAdvice} модулей ({@code auth.web},
 * {@code scenario.web}: см. {@code AuthExceptionHandler}/{@code ScenarioExceptionHandler}).
 *
 * <p>{@code @Order(Ordered.LOWEST_PRECEDENCE)} — этот advice применяется ко ВСЕМ контроллерам
 * (без {@code basePackages}), в том числе к тем, что уже покрыты доменными advice с более высоким
 * приоритетом ({@code @Order} на них ниже числом): при наличии конфликта по типу исключения
 * Spring перебирает применимые advice-бины в порядке {@code @Order} и берёт первый подходящий
 * метод, так что доменные обработчики всегда выигрывают у этого для своих типов исключений, а
 * этот остаётся честным fallback'ом для остальных модулей (gamification/feedback) и framework-уровня
 * везде.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    /** {@code @Valid}-нарушения тела запроса (Bean Validation) — список "поле: причина" в {@code details}. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "validation_failed", "Ошибка валидации запроса", details, request);
    }

    /** {@code @PathVariable}/{@code @RequestParam} не приводится к нужному типу (например, невалидный UUID). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String message = "Параметр '%s' должен быть типа %s, получено: '%s'".formatted(
                e.getName(),
                e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "?",
                e.getValue());
        return respond(HttpStatus.BAD_REQUEST, "bad_request", message, null, request);
    }

    /** Обязательный {@code @RequestParam} не передан. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(
            MissingServletRequestParameterException e, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "bad_request", e.getMessage(), null, request);
    }

    /** Тело запроса не парсится как ожидаемый JSON/формат (битый JSON, несовместимый тип поля и т.п.). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "bad_request", "Тело запроса не распознано", null, request);
    }

    /**
     * Ручной {@code throw new ResponseStatusException(...)} (например, невалидный UUID в
     * заголовке — {@code TeamController.parsePlayerId}) — код по умолчанию берётся из HTTP-статуса,
     * если у конкретного места не было причины завести свой доменный код.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException e, HttpServletRequest request) {
        HttpStatusCode statusCode = e.getStatusCode();
        String message = e.getReason() != null ? e.getReason() : statusCode.toString();
        return respond(HttpStatus.valueOf(statusCode.value()), defaultCodeFor(statusCode), message, null, request);
    }

    /** Путь не смэплен ни на один контроллер и ни на один статический ресурс. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException e, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "not_found", "Путь не найден: " + request.getRequestURI(), null, request);
    }

    /** Путь существует, но не для этого HTTP-метода. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed", e.getMessage(), null, request);
    }

    /**
     * Последний рубеж: необработанное исключение любого другого типа. Стектрейс — только в лог
     * (см. {@code log.error}), наружу — только код/сообщение без внутренних деталей приложения.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Необработанное исключение на {} {}", request.getMethod(), request.getRequestURI(), e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Внутренняя ошибка сервера", null, request);
    }

    private String defaultCodeFor(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 400 -> "bad_request";
            case 401 -> "unauthorized";
            case 403 -> "access_denied";
            case 404 -> "not_found";
            case 405 -> "method_not_allowed";
            case 409 -> "conflict";
            default -> "error";
        };
    }

    private ResponseEntity<ApiError> respond(
            HttpStatus status, String code, String message, List<String> details, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(code, message, details, request.getRequestURI()));
    }
}
