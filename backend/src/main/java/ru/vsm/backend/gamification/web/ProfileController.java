package ru.vsm.backend.gamification.web;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.gamification.service.GamificationQueryService;
import ru.vsm.backend.gamification.web.dto.ProfileResponse;

/**
 * GET /api/gamification/profile/{playerId} — см. design/screens/profile.md. Идентификация
 * игрока — по id в пути (в MVP нет домена аутентификации, playerId = userId из
 * ScenarioCompletedEvent; фронт передаёт его как есть). Для игрока без прохождений возвращает
 * DTO с нулями, не 404 — так фронт рисует пустое состояние, а не ошибку.
 */
@RestController
@RequestMapping("/api/gamification/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final GamificationQueryService queryService;

    @GetMapping("/{playerId}")
    public ProfileResponse getProfile(@PathVariable UUID playerId) {
        return queryService.getProfile(playerId);
    }
}
