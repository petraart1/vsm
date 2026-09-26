package ru.vsm.backend.config.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Единая форма тела ошибки для всего REST API (все домены и слой Spring Security/framework).
 *
 * <p>{@code error} — стабильный машиночитаемый код (например {@code progress_already_completed},
 * {@code invalid_graph}, {@code invalid_markdown}, {@code login_already_taken},
 * {@code validation_failed}, {@code bad_request}, {@code not_found}, {@code internal_error}).
 * Существующие коды доменных исключений (см. модульные {@code @RestControllerAdvice}) при переходе
 * на эту форму не переименованы — клиенты уже на них полагаются, изменилась только форма тела, не
 * значения кодов.
 *
 * <p>{@code details} — необязательный список уточнений (ошибки валидации по полям, список проблем
 * графа сценария и т.п.); {@code null} и не попадает в JSON ({@link JsonInclude.Include#NON_NULL}),
 * когда для конкретной ошибки уточнений нет — только {@code message} с одним связным текстом.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String error,
        String message,
        List<String> details,
        Instant timestamp,
        String path) {

    public ApiError(String error, String message, String path) {
        this(error, message, null, Instant.now(), path);
    }

    public ApiError(String error, String message, List<String> details, String path) {
        this(error, message, details, Instant.now(), path);
    }
}
