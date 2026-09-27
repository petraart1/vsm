package ru.vsm.mobile.domain.model

/** Мини-прогресс по одному блоку ситуаций. */
data class BlockProgress(
    val block: String,
    val scenariosCompleted: Int,
    val loyaltyPoints: Int,
    val safetyPoints: Int,
)

/**
 * Одна ачивка из каталога с состоянием для конкретного игрока (либо "не определён", если запрос
 * анонимный — тогда все ачивки [earned] == false). [earnedAt] — момент получения в формате
 * ISO-8601, `null` пока ачивка не получена.
 */
data class Achievement(
    val code: String,
    val title: String,
    val description: String,
    val category: String,
    val earned: Boolean,
    val earnedAt: String?,
)

/**
 * Профиль игрока. Для нового игрока без завершённых прохождений возвращается с нулевыми
 * счётчиками и пустыми списками — "пустое" состояние профиля, а не ошибка.
 */
data class Profile(
    val playerId: String,
    val displayName: String,
    val totalScore: Int,
    val scenariosCompleted: Int,
    val totalScenariosAvailable: Int,
    val blockProgress: List<BlockProgress>,
    val recentAchievements: List<Achievement>,
    val leaderboardRank: Long?,
)

data class LeaderboardEntry(
    val rank: Long,
    val playerId: String,
    val displayName: String,
    val totalScore: Int,
    val scenariosCompleted: Int,
)

/**
 * [me] — закреплённая карточка "Ваше место", заполнена только если для игрока есть профиль;
 * иначе `null` (игрок ещё не участвует).
 */
data class Leaderboard(
    val top: List<LeaderboardEntry>,
    val me: LeaderboardEntry?,
)

/** Элемент списка уведомлений игрока (новая ачивка / личный рекорд / рост в лидерборде / рекомендация). */
data class Notification(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val createdAt: String,
    val readAt: String?,
) {
    val unread: Boolean get() = readAt == null
}
