package ru.vsm.backend.gamification.expiry.web;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.gamification.expiry.service.PointsExpiryService;
import ru.vsm.backend.gamification.expiry.web.dto.PointsExpiryRunResult;

/** Демо-эндпоинт: запускает сгорание баллов за неактивность вне расписания (см. */
@RestController
@RequestMapping("/api/admin/points-expiry")
@RequiredArgsConstructor
public class AdminPointsExpiryController {

    private final PointsExpiryService pointsExpiryService;

    /** @param now необязательная симулированная "текущая" дата (ISO-8601 instant, например */
    @PostMapping("/run")
    public PointsExpiryRunResult run(@RequestParam(required = false) Instant now) {
        return pointsExpiryService.run(now != null ? now : Instant.now());
    }
}
