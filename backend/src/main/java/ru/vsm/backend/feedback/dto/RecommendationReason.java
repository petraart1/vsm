package ru.vsm.backend.feedback.dto;

/** Почему сценарий попал в рекомендации {@link CompetencyAnalyticsResponse#recommendations()}. */
public enum RecommendationReason {
    /** Игрок ни разу не начинал этот сценарий (нет записи {@code user_progress} в любом статусе). */
    NOT_PLAYED,
    /** Последнее завершённое прохождение этого сценария закончилось исходом FAILURE. */
    FAILED,
    /** Последнее завершённое прохождение этого сценария закончилось исходом PARTIAL. */
    PARTIAL
}
