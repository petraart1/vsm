package ru.vsm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/** Провод-формат enum'ов backend (сериализуются Jackson по имени константы) — 1:1 с `ru.vsm.backend.scenario.domain.*`. */
@Serializable
enum class NodeTypeDto { DIALOGUE, ESCALATION, TERMINAL }

@Serializable
enum class ScenarioOutcomeDto { SUCCESS, PARTIAL, FAILURE }

@Serializable
enum class ProgressStatusDto { IN_PROGRESS, COMPLETED, ABANDONED }

@Serializable
enum class RoleStepDto { ACKNOWLEDGE, RULE, SOLUTION, REASSURE }

@Serializable
enum class RecommendationReasonDto { NOT_PLAYED, FAILED, PARTIAL }

@Serializable
enum class CarClassDto { STANDARD, COMFORT, BUSINESS, FIRST }

@Serializable
enum class ExamStatusDto { IN_PROGRESS, COMPLETED }

@Serializable
enum class ExamGradeDto { EXCELLENT, GOOD, SATISFACTORY, UNSATISFACTORY }

/**
 * `ru.vsm.backend.gamification.domain.NotificationType` — [UNKNOWN] не существует на backend,
 * это локальный запасной вариант: вместе с `coerceInputValues = true` в конфигурации `Json`
 * (см. `AppContainer`) он подставляется вместо падения парсинга, если backend когда-нибудь
 * добавит новый тип раньше, чем обновится клиент.
 */
@Serializable
enum class NotificationTypeDto {
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
