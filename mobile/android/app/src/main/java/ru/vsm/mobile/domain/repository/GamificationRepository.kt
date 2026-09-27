package ru.vsm.mobile.domain.repository

import ru.vsm.mobile.domain.model.Achievement
import ru.vsm.mobile.domain.model.Challenge
import ru.vsm.mobile.domain.model.CustomAward
import ru.vsm.mobile.domain.model.Leaderboard
import ru.vsm.mobile.domain.model.Notification
import ru.vsm.mobile.domain.model.Profile
import ru.vsm.mobile.domain.model.Showcase
import ru.vsm.mobile.domain.model.ShowcaseItem
import ru.vsm.mobile.domain.model.Team
import ru.vsm.mobile.domain.model.TeamLeaderboardEntry

/**
 * Геймификация: профиль игрока, ачивки, кастомные награды, витрина, команды, лидерборды (личный
 * и командный), челленджи месяца, уведомления.
 */
interface GamificationRepository {

    /** Профиль игрока (счёт, уровень, прогресс по блокам, недавние ачивки). Без прохождений — нулевой, не 404. */
    suspend fun getProfile(playerId: String): Result<Profile>

    /**
     * Топ лидерборда и, если запрашивающего можно опознать и для него есть профиль, закреплённая
     * карточка "Ваше место" ([Leaderboard.me]) — она может быть вне топа.
     */
    suspend fun getLeaderboard(limit: Int = 20, playerId: String? = null): Result<Leaderboard>

    /** Рейтинг команд по среднему баллу участника, публичный, без идентификации. */
    suspend fun getTeamLeaderboard(): Result<List<TeamLeaderboardEntry>>

    /** Полный каталог ачивок. Без [playerId] все ачивки возвращаются как не полученные. */
    suspend fun getAchievements(playerId: String? = null): Result<List<Achievement>>

    /** Каталог наград администратора. Без [playerId] — без отметок о получении. */
    suspend fun getCustomAwards(playerId: String? = null): Result<List<CustomAward>>

    /** Активные челленджи месяца с прогрессом игрока. Без [playerId] — с нулевым прогрессом. */
    suspend fun getChallenges(playerId: String? = null): Result<List<Challenge>>

    /** Заменяет витрину [playerId] целиком (не более 6 наград), возвращает сохранённое состояние. */
    suspend fun putShowcase(playerId: String, finish: String, items: List<ShowcaseItem>): Result<Showcase>

    /** Публичная витрина коллеги по `publicId` из лидерборда ([ru.vsm.mobile.domain.model.LeaderboardEntry.publicId]). */
    suspend fun getShowcase(publicId: String): Result<Showcase>

    /** Каталог команд (бригад/депо). */
    suspend fun getTeams(): Result<List<Team>>

    /** Вступление [playerId] в команду [teamId]; повторный вызов с другим id — смена команды. */
    suspend fun joinTeam(teamId: String, playerId: String): Result<Team>

    /** Список уведомлений игрока. */
    suspend fun getNotifications(playerId: String, unreadOnly: Boolean = false): Result<List<Notification>>

    /** Отмечает одно уведомление прочитанным, возвращает его обновлённое состояние. [playerId] — владелец уведомления. */
    suspend fun markRead(notificationId: String, playerId: String): Result<Notification>

    /** Отмечает все уведомления игрока прочитанными, возвращает число отмеченных. */
    suspend fun markAllRead(playerId: String): Result<Int>
}
