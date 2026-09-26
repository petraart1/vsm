package ru.vsm.backend.auth.web.dto;

/** Тело {@code POST /api/auth/register}. Валидация — в {@code AuthRegistrationService}. */
public record RegisterRequest(String login, String email, String password, String displayName) {
}
