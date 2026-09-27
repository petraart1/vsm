package ru.vsm.backend.auth.web.dto;

import ru.vsm.backend.auth.domain.UserRole;

/**
 * Тело {@code PATCH /api/admin/users/{id}} — частичное обновление, {@code null}-поле оставляет
 * значение как есть (семантика PATCH, а не PUT). Пароль этим эндпоинтом не меняется.
 */
public record AdminUserPatchRequest(UserRole role, Boolean verified, String displayName) {
}
