package ru.vsm.backend.auth.web.dto;

/** Тело {@code POST /api/auth/login}. */
public record LoginRequest(String login, String password) {
}
