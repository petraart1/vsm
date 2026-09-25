package ru.vsm.backend.gamification.web;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.gamification.service.GamificationQueryService;
import ru.vsm.backend.gamification.web.dto.LeaderboardResponse;

/**
 * GET /api/gamification/leaderboard?limit=N&amp;playerId=... — см. design/screens/leaderboard.md.
 * {@code playerId} опционален и заполняет закреплённую карточку "Ваше место" (может быть
 * вне топа); без него/для игрока без прохождений {@code me} в ответе — null.
 */
@RestController
@RequestMapping("/api/gamification/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final GamificationQueryService queryService;

    @GetMapping
    public LeaderboardResponse getLeaderboard(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) UUID playerId) {
        return queryService.getLeaderboard(limit, playerId);
    }
}
