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

/**
 * REST аналитики компетенций игрока — агрегат по всем завершённым прохождениям, в отличие от
 * разбора одного прохождения ({@link DebriefController}).
 *
 * <p>Эндпоинт: {@code GET /api/feedback/competencies/{playerId}} -> {@link CompetencyAnalyticsResponse}.
 * {@code playerId} — тот же id, что используется как {@code userId} в scenario и {@code playerId}
 * в gamification. Игрок без завершённых прохождений получает 200 с нулевым агрегатом, не 404 —
 * тот же принцип, что у {@code GamificationQueryService.getProfile}.
 *
 * <p>Доступ — только владелец (JWT либо {@code X-Player-Id}, совпадающий с {@code playerId} в
 * пути) или ADMIN, иначе {@code 403 forbidden} (см. {@link PlayerAccessGuard}) — раньше аналитика
 * компетенций любого игрока читалась без единой проверки владения.
 */
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
