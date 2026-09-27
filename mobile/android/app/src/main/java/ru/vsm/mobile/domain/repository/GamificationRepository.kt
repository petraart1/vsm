package ru.vsm.mobile.domain.repository

import ru.vsm.mobile.domain.model.Achievement
import ru.vsm.mobile.domain.model.Leaderboard
import ru.vsm.mobile.domain.model.Notification
import ru.vsm.mobile.domain.model.Profile

/** Геймификация: профиль игрока, ачивки, лидерборд, уведомления. */
interface GamificationRepository {

    /** Профиль игрока (счёт, прогресс по блокам, недавние ачивки). Без прохождений — нулевой, не 404. */
    suspend fun getProfile(playerId: String): Result<Profile>

    /**
     * Топ лидерборда и, если для игрока есть профиль, закреплённая карточка "Ваше место"
     * ([Leaderboard.me]) — она может быть вне топа.
     */
    suspend fun getLeaderboard(limit: Int = 20, playerId: String? = null): Result<Leaderboard>

    /** Полный каталог ачивок. Без [playerId] все ачивки возвращаются как не полученные. */
    suspend fun getAchievements(playerId: String? = null): Result<List<Achievement>>

    /** Список уведомлений игрока. */
    suspend fun getNotifications(playerId: String, unreadOnly: Boolean = false): Result<List<Notification>>

    /** Отмечает одно уведомление прочитанным, возвращает его обновлённое состояние. */
    suspend fun markRead(notificationId: String): Result<Notification>

    /** Отмечает все уведомления игрока прочитанными, возвращает число отмеченных. */
    suspend fun markAllRead(playerId: String): Result<Int>
}
