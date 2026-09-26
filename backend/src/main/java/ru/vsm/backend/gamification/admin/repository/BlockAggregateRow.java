package ru.vsm.backend.gamification.admin.repository;

/** JPQL-проекция {@link AdminProgressStatsRepository#aggregateByBlock()}, одна строка на блок ситуаций. */
public record BlockAggregateRow(
        String block,
        Long totalPlaythroughs,
        Long completedCount,
        Long successCount,
        Double avgLoyalty,
        Double avgSafety) {
}
