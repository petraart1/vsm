package ru.vsm.backend.gamification.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.auth.security.PlayerAccessGuard;
import ru.vsm.backend.gamification.service.GamificationQueryService;
import ru.vsm.backend.gamification.web.dto.ProfileResponse;

/**
 * GET /api/gamification/profile/{playerId} — см. design/screens/profile.md. Идентификация
 * игрока — по id в пути (в MVP нет домена аутентификации, playerId = userId из
 * ScenarioCompletedEvent; фронт передаёт его как есть). Для игрока без прохождений возвращает
 * DTO с нулями, не 404 — так фронт рисует пустое состояние, а не ошибку.
 *
 * <p>Доступ — только владелец (JWT либо {@code X-Player-Id}, совпадающий с {@code playerId} в
 * пути) или ADMIN, иначе {@code 403 forbidden} (см. {@link PlayerAccessGuard}) — раньше чужой
 * профиль читался без единой проверки, доставало знать/угадать {@code playerId}.
 */
@RestController
@RequestMapping("/api/gamification/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final GamificationQueryService queryService;
    private final PlayerAccessGuard playerAccessGuard;

    @GetMapping("/{playerId}")
    public ProfileResponse getProfile(@PathVariable UUID playerId, HttpServletRequest request) {
        playerAccessGuard.requireOwnerOrAdmin(request, playerId);
        return queryService.getProfile(playerId);
    }
}
