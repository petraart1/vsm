package ru.vsm.backend.gamification.admin.repository;

import java.time.Instant;
import java.util.UUID;

/** JPQL-проекция {@link AdminProgressStatsRepository#aggregateByPlayer()}, одна строка на игрока. */
public record PlayerAggregateRow(
        UUID playerId,
        Long totalPlaythroughs,
        Long completedCount,
        Long successCount,
        Double avgLoyalty,
        Double avgSafety,
        Instant lastActivity) {
}
