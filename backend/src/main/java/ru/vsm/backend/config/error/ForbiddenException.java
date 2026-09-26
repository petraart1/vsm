package ru.vsm.backend.config.error;

/**
 * Запрошены данные другого игрока без прав на это (см. {@code ru.vsm.backend.auth.security.PlayerAccessGuard}) —
 * отдельный код {@code forbidden} (не переиспользует {@code access_denied}, который уже занят
 * ошибками роли ADMIN в {@code SecurityConfig}), чтобы клиент мог различить "нет роли" и "не твои данные".
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
