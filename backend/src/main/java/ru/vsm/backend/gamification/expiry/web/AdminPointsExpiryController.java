package ru.vsm.backend.gamification.expiry.web;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.gamification.expiry.service.PointsExpiryService;
import ru.vsm.backend.gamification.expiry.web.dto.PointsExpiryRunResult;

/**
 * Демо-эндпоинт: запускает сгорание баллов за неактивность вне расписания (см.
 * {@code PointsExpiryService#runScheduled}, план — раз в сутки). Путь под {@code /api/admin/**},
 * поэтому доступен только роли ADMIN (см. {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/admin/points-expiry")
@RequiredArgsConstructor
public class AdminPointsExpiryController {

    private final PointsExpiryService pointsExpiryService;

    /**
     * @param now необязательная симулированная "текущая" дата (ISO-8601 instant, например
     *            {@code 2026-10-15T00:00:00Z}) — чтобы продемонстрировать сгорание не дожидаясь
     *            реальных {@code inactivityDays} дней неактивности. Без параметра — реальное
     *            {@code Instant.now()}.
     */
    @PostMapping("/run")
    public PointsExpiryRunResult run(@RequestParam(required = false) Instant now) {
        return pointsExpiryService.run(now != null ? now : Instant.now());
    }
}
