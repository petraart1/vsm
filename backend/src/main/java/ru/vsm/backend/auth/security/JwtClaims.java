package ru.vsm.backend.auth.security;

import java.util.UUID;
import ru.vsm.backend.auth.domain.UserRole;

/** Полезная нагрузка проверенного токена: {@code subject} — playerId, плюс роль и логин. */
public record JwtClaims(UUID playerId, UserRole role, String login) {
}
