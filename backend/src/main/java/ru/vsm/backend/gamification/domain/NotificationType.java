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
    RECOMMENDED_SCENARIO,

    /** Выполнен челлендж месяца (см. {@code gamification.challenge}). */
    CHALLENGE_COMPLETED,

    /** Команда игрока поднялась на 1-е место в командном рейтинге (см. {@code gamification.team}). */
    TEAM_RANK_UP,

    /**
     * Экзамен завершён: оценка и бонусные очки (см. {@code ExamAccrualService},
     * {@code ru.vsm.backend.scenario.event.ExamCompletedEvent}). Отдельно от этого уведомления,
     * если оценка "отлично" — выдаётся ещё и {@code ACHIEVEMENT_UNLOCKED} за {@link
     * AchievementCode#CERTIFICATE}.
     */
    EXAM_COMPLETED,

    /**
     * Появился новый сценарий (создан через редактор сценариев или импортирован из markdown —
     * см. {@code ru.vsm.backend.scenario.event.ScenarioPublishedEvent}). Рассылается всем уже
     * известным профилям игрока ({@code gamification_player_profile}); сид сценариев при старте
     * приложения уведомлений не создаёт (только реальная публикация через редактор).
     */
    NEW_SCENARIO,

    /**
     * Создано новое событие/челлендж (см. {@code gamification.challenge}) администратором —
     * рассылается всем известным профилям игрока. В отличие от {@code CHALLENGE_COMPLETED}
     * (личное уведомление о выполнении), это уведомление о самом факте появления цели.
     */
    NEW_CHALLENGE,

    /**
     * Игрок приближается к порогу неактивности, после которого часть накопленных баллов
     * сгорит (см. {@code gamification.expiry.service.PointsExpiryService}). Создаётся не более
     * одного раза за текущий период неактивности.
     */
    POINTS_EXPIRING,

    /**
     * Часть накопленных баллов сгорела из-за длительной неактивности (см.
     * {@code gamification.expiry.service.PointsExpiryService}); текст уведомления содержит
     * число сгоревших баллов.
     */
    POINTS_EXPIRED
}
