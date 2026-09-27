package ru.vsm.backend.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.vsm.backend.config.error.ForbiddenException;

/** Единая точка проверки "запрашивает свои данные или ADMIN" для эндпоинтов, идентифицирующих */
@Component
public class PlayerAccessGuard {

    public static final String PLAYER_ID_HEADER = "X-Player-Id";
    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    @Value("${app.auth.require-token:false}")
    private boolean requireToken;

    /** Личность запрашивающего, если её можно установить (токен либо, если разрешено, заголовок). */
    public Optional<UUID> currentPlayerId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UUID jwtPlayerId) {
            return Optional.of(jwtPlayerId);
        }
        if (requireToken) {
            return Optional.empty();
        }
        String header = request.getHeader(PLAYER_ID_HEADER);
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(header));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Заголовок " + PLAYER_ID_HEADER + " должен быть UUID, получено: '" + header + "'");
        }
    }

    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> ADMIN_AUTHORITY.equals(a.getAuthority()));
    }

    /** Бросает {@link ForbiddenException} (403 {@code forbidden}), если запрашивающий не владелец */
    public void requireOwnerOrAdmin(HttpServletRequest request, UUID targetPlayerId) {
        if (isAdmin()) {
            return;
        }
        Optional<UUID> requester = currentPlayerId(request);
        if (requester.isEmpty() || !requester.get().equals(targetPlayerId)) {
            throw new ForbiddenException("Доступ к данным игрока " + targetPlayerId + " запрещён");
        }
    }
}
