package ru.vsm.backend.auth.web.esia.dto;

import jakarta.validation.constraints.NotBlank;

/** Тело {@code POST /api/auth/esia/callback} — код, полученный после редиректа с {@code /authorize}. */
public record EsiaCallbackRequest(@NotBlank(message = "код обязателен") String code) {
}
