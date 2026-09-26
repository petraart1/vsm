package ru.vsm.backend.gamification.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.auth.security.PlayerAccessGuard;
import ru.vsm.backend.gamification.service.GamificationQueryService;
import ru.vsm.backend.gamification.web.dto.LeaderboardResponse;

/**
 * GET /api/gamification/leaderboard?limit=N — см. design/screens/leaderboard.md. Публичный,
 * без аутентификации: строки не содержат реального {@code playerId} (см.
 * {@code ru.vsm.backend.gamification.web.dto.LeaderboardEntryDto}). "Ваше место" ({@code me} в
 * ответе) заполняется по личности самого запроса (JWT либо {@code X-Player-Id}), а не по
 * query-параметру — раньше {@code ?playerId=} позволял подставить чужой id и прочитать его
 * строку как "свою" (см. находку CRITICAL в аудите безопасности).
 */
@RestController
@RequestMapping("/api/gamification/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final GamificationQueryService queryService;
    private final PlayerAccessGuard playerAccessGuard;

    @GetMapping
    public LeaderboardResponse getLeaderboard(
            @RequestParam(defaultValue = "20") int limit, HttpServletRequest request) {
        UUID requesterId = playerAccessGuard.currentPlayerId(request).orElse(null);
        return queryService.getLeaderboard(limit, requesterId);
    }
}
