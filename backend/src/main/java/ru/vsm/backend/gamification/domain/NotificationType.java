package ru.vsm.backend.gamification.domain;

/**
 * Тип уведомления игрока. Создаются в {@code GamificationAccrualService} в том же обработчике
 * {@code ScenarioCompletedEvent}, что и начисление очков.
 */
public enum NotificationType {

    /** Разблокирована новая ачивка (см. {@link AchievementCode}). */
    ACHIEVEMENT_UNLOCKED,

    /** Новый личный рекорд по очкам за одно прохождение конкретного сценария. */
    NEW_PERSONAL_BEST,

    /** Позиция в лидерборде улучшилась по сравнению с позицией до этого прохождения. */
    LEADERBOARD_RANK_UP,

    /** Рекомендованный сценарий (точка расширения; в MVP не создаётся начислением). */
    RECOMMENDED_SCENARIO
}
