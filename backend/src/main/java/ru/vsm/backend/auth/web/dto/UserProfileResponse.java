package ru.vsm.backend.auth.web.dto;

import java.time.Instant;
import java.util.UUID;
import ru.vsm.backend.auth.domain.AppUser;
import ru.vsm.backend.auth.domain.UserRole;

/**
 * Профиль учётной записи без пароля — ответ регистрации/логина/{@code /api/auth/me}.
 *
 * @param verified подтверждённая личность (сейчас — только через демо-заглушку Госуслуг/ЕСИА,
 *                 см. {@code ru.vsm.backend.auth.esia}); обычная регистрация логином/паролем даёт {@code false}
 */
public record UserProfileResponse(
        UUID id, String login, String email, String displayName, UserRole role, boolean verified, Instant createdAt) {

    public static UserProfileResponse from(AppUser user) {
        return new UserProfileResponse(
                user.getId(), user.getLogin(), user.getEmail(), user.getDisplayName(),
                user.getRole(), user.isVerified(), user.getCreatedAt());
    }
}
