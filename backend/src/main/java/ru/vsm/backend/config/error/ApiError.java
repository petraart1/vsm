package ru.vsm.backend.config.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/** Единая форма тела ошибки для всего REST API (все домены и слой Spring Security/framework). */
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
