package ru.vsm.backend.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.vsm.backend.auth.esia.service.exception.InvalidEsiaCodeException;
import ru.vsm.backend.auth.service.exception.InvalidCredentialsException;
import ru.vsm.backend.auth.service.exception.InvalidTokenException;
import ru.vsm.backend.auth.service.exception.UserAlreadyExistsException;
import ru.vsm.backend.config.error.ApiError;

/**
 * Маппинг исключений домена auth в HTTP-ответы с единой формой {@link ApiError}. {@code @Order(10)}
 * — выше приоритетом (проверяется раньше), чем framework-уровневый
 * {@code ru.vsm.backend.config.error.GlobalExceptionHandler} (LOWEST_PRECEDENCE), для случаев,
 * когда оба advice-бина применимы к одному контроллеру (например {@code IllegalArgumentException}
 * не переопределяется в глобальном обработчике именно поэтому).
 */
@RestControllerAdvice(basePackages = "ru.vsm.backend.auth.web")
@Order(10)
public class AuthExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiError> handle(UserAlreadyExistsException e, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handle(InvalidCredentialsException e, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, e.getMessage(), request);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiError> handle(InvalidTokenException e, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, e.getMessage(), request);
    }

    @ExceptionHandler(InvalidEsiaCodeException.class)
    public ResponseEntity<ApiError> handle(InvalidEsiaCodeException e, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handle(IllegalArgumentException e, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    /** Исторически код ошибки этого модуля = сообщение исключения (например {@code login_already_taken}). */
    private ResponseEntity<ApiError> respond(HttpStatus status, String code, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(code, code, request.getRequestURI()));
    }
}
