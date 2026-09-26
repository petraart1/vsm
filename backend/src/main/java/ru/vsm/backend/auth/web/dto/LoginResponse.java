package ru.vsm.backend.auth.web.dto;

/** Ответ {@code POST /api/auth/login}: токен для заголовка {@code Authorization: Bearer <token>} + профиль. */
public record LoginResponse(String token, UserProfileResponse profile) {
}
