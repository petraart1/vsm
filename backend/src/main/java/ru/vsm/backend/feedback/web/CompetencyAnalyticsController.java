package ru.vsm.backend.feedback.web;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.feedback.dto.CompetencyAnalyticsResponse;
import ru.vsm.backend.feedback.service.CompetencyAnalyticsService;

/**
 * REST аналитики компетенций игрока — агрегат по всем завершённым прохождениям, в отличие от
 * разбора одного прохождения ({@link DebriefController}).
 *
 * <p>Эндпоинт: {@code GET /api/feedback/competencies/{playerId}} -> {@link CompetencyAnalyticsResponse}.
 * {@code playerId} — тот же id, что используется как {@code userId} в scenario и {@code playerId}
 * в gamification. Игрок без завершённых прохождений получает 200 с нулевым агрегатом, не 404 —
 * тот же принцип, что у {@code GamificationQueryService.getProfile}.
 */
@RestController
@RequiredArgsConstructor
public class CompetencyAnalyticsController {

    private final CompetencyAnalyticsService competencyAnalyticsService;

    @GetMapping("/api/feedback/competencies/{playerId}")
    public CompetencyAnalyticsResponse getCompetencyAnalytics(@PathVariable UUID playerId) {
        return competencyAnalyticsService.analyze(playerId);
    }
}
