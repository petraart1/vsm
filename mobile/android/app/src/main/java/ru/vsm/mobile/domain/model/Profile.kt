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
 *
 * @param level текущий уровень игрока по общему счёту ([totalScore]).
 * @param levelTitle человекочитаемое звание уровня (см. `ru.vsm.backend.gamification.domain.PlayerLevel`).
 * @param levelProgress прогресс внутри текущего уровня, 0..100.
 * @param pointsToNextLevel очков не хватает до следующего уровня; `null` — уже максимальный уровень.
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
    val level: Int,
    val levelTitle: String,
    val levelProgress: Int,
    val pointsToNextLevel: Int?,
)

/**
 * Строка публичного лидерборда — намеренно без реального `playerId` (см. javadoc на backend):
 * [publicId] — стабильный, но необратимый к исходному id идентификатор (используется, например,
 * для публичной витрины наград, см. [ru.vsm.mobile.domain.repository.GamificationRepository.getShowcase]).
 * [me] — эта строка принадлежит текущему запрашивающему.
 */
data class LeaderboardEntry(
    val rank: Long,
    val publicId: String,
    val displayName: String,
    val totalScore: Int,
    val scenariosCompleted: Int,
    val me: Boolean,
    val level: Int,
    val levelTitle: String,
)

/**
 * [me] — закреплённая карточка "Ваше место", заполнена только если запрашивающего можно опознать
 * (сохранённый токен или переданный playerId) и для него есть профиль; иначе `null`.
 */
data class Leaderboard(
    val top: List<LeaderboardEntry>,
    val me: LeaderboardEntry?,
)

/** Элемент списка уведомлений игрока (новая ачивка / личный рекорд / рост в лидерборде / рекомендация). */
data class Notification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val createdAt: String,
    val readAt: String?,
) {
    val unread: Boolean get() = readAt == null
}
