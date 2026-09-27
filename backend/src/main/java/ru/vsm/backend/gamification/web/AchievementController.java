package ru.vsm.backend.gamification.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.auth.security.PlayerAccessGuard;
import ru.vsm.backend.gamification.service.GamificationQueryService;
import ru.vsm.backend.gamification.web.dto.AchievementDto;

/** GET /api/gamification/achievements?playerId=... — полный каталог (см. */
@RestController
@RequestMapping("/api/gamification/achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final GamificationQueryService queryService;
    private final PlayerAccessGuard playerAccessGuard;

    @GetMapping
    public List<AchievementDto> getAchievements(
            @RequestParam(required = false) UUID playerId, HttpServletRequest request) {
        if (playerId != null) {
            playerAccessGuard.requireOwnerOrAdmin(request, playerId);
        }
        return queryService.getAchievementCatalog(playerId);
    }
}
