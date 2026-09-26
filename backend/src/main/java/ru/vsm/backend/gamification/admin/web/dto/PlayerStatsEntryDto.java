package ru.vsm.backend.gamification.admin.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Одна строка статистики по игроку — источник для {@code GET /api/admin/stats/players} (JSON) и
 * {@code /players.csv}. {@code teamName} — {@code null}, если игрок ни в какой команде не состоит.
 * {@code successRate} — доля {@code COMPLETED} прохождений с {@code finalOutcome = SUCCESS}, та же
 * семантика, что у {@link ScenarioStatsEntryDto#successRate()}. {@code lastActivity} — момент
 * последнего обновления любого прохождения игрока (не только завершённых).
 */
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
