package ru.vsm.backend.gamification.web;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.gamification.service.GamificationQueryService;
import ru.vsm.backend.gamification.web.dto.AchievementDto;

/**
 * GET /api/gamification/achievements?playerId=... — полный каталог (см.
 * design/screens/achievements.md). Без {@code playerId} все ачивки возвращаются как
 * {@code earned=false} (локальный просмотр каталога целей без привязки к игроку).
 */
@RestController
@RequestMapping("/api/gamification/achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final GamificationQueryService queryService;

    @GetMapping
    public List<AchievementDto> getAchievements(@RequestParam(required = false) UUID playerId) {
        return queryService.getAchievementCatalog(playerId);
    }
}
