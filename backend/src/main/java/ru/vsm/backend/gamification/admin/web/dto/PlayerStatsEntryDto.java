package ru.vsm.backend.gamification.admin.web.dto;

import java.time.Instant;
import java.util.UUID;

/** Одна строка статистики по игроку — источник для {@code GET /api/admin/stats/players} (JSON) и */
public record PlayerStatsEntryDto(
        UUID playerId,
        String displayName,
        String teamName,
        int totalScore,
        long totalPlaythroughs,
        double successRate,
        double avgLoyaltyScore,
        double avgSafetyScore,
        Instant lastActivity) {
}
