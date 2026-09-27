package ru.vsm.backend.feedback.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.auth.security.PlayerAccessGuard;
import ru.vsm.backend.feedback.dto.CompetencyAnalyticsResponse;
import ru.vsm.backend.feedback.service.CompetencyAnalyticsService;

/** REST аналитики компетенций игрока — агрегат по всем завершённым прохождениям, в отличие от */
@RestController
@RequiredArgsConstructor
public class CompetencyAnalyticsController {

    private final CompetencyAnalyticsService competencyAnalyticsService;
    private final PlayerAccessGuard playerAccessGuard;

    @GetMapping("/api/feedback/competencies/{playerId}")
    public CompetencyAnalyticsResponse getCompetencyAnalytics(
            @PathVariable UUID playerId, HttpServletRequest request) {
        playerAccessGuard.requireOwnerOrAdmin(request, playerId);
        return competencyAnalyticsService.analyze(playerId);
    }
}
