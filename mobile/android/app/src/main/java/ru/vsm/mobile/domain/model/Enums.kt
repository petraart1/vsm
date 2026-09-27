package ru.vsm.mobile.domain.model

/** Тип узла графа сценария. */
enum class NodeType {
    /** Обычная реплика/ситуация с выбором ответа. */
    DIALOGUE,

    /** Узел эскалации (вызов начальника поезда, ПТБ, медиков и т.п.). */
    ESCALATION,

    /** Терминальный узел — конец ветки с исходом. */
    TERMINAL,
}

/** Итог терминального узла или прохождения сценария целиком. */
enum class ScenarioOutcome {
    SUCCESS,
    PARTIAL,
    FAILURE,
}

/** Статус прохождения сценария игроком. */
enum class ProgressStatus {
    IN_PROGRESS,
    COMPLETED,
    ABANDONED,
}

/** Шаг универсальной 4-шаговой ролевой модели ответа: признать -> обозначить правило -> предложить решение -> заверить. */
enum class RoleStep {
    ACKNOWLEDGE,
    RULE,
    SOLUTION,
    REASSURE,
}

/** Почему сценарий попал в рекомендации [ru.vsm.mobile.domain.model.CompetencyAnalytics.recommendations]. */
enum class RecommendationReason {
    /** Игрок ни разу не начинал этот сценарий. */
    NOT_PLAYED,

    /** Последнее завершённое прохождение этого сценария закончилось неудачей. */
    FAILED,

    /** Последнее завершённое прохождение этого сценария закончилось частичным успехом. */
    PARTIAL,
}

/** Роль учётной записи ([AuthUser.role]) — пока ни на одном экране не влияет на доступ, только на отображение. */
enum class UserRole {
    USER,
    ADMIN,
}

/**
 * Класс обслуживания вагона ВСМ ("портрет пассажира" — Стандарт/Комфорт/Бизнес/Первый). Влияет на
 * модификатор [ChoiceResult.loyaltyDelta] при обычном прохождении (сильнее наказывает и слабее
 * хвалит в верхних классах, см. бэкенд); для [Exam] — единый класс, зафиксированный для всех его
 * пунктов при создании.
 */
enum class CarClass {
    STANDARD,
    COMFORT,
    BUSINESS,
    FIRST,
}

/**
 * Тип уведомления игрока ([Notification.type]) — 1:1 с `ru.vsm.backend.gamification.domain.NotificationType`.
 * [UNKNOWN] не приходит с backend, это безопасный запасной вариант на случай нового типа,
 * ещё не известного этой версии клиента (см. `NotificationTypeDto`) — UI должен уметь отрисовать
 * его как обычное уведомление (заголовок/текст всё равно приходят), просто без специфичной иконки.
 */
enum class NotificationType {
    ACHIEVEMENT_UNLOCKED,
    NEW_PERSONAL_BEST,
    LEADERBOARD_RANK_UP,
    RECOMMENDED_SCENARIO,
    CHALLENGE_COMPLETED,
    TEAM_RANK_UP,
    EXAM_COMPLETED,
    NEW_SCENARIO,
    NEW_CHALLENGE,
    POINTS_EXPIRING,
    POINTS_EXPIRED,
    LEVEL_UP,
    UNKNOWN,
}
