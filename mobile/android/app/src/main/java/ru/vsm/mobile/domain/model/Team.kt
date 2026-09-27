package ru.vsm.mobile.domain.model

/** Команда (бригада/депо) — каталог и результат вступления. */
data class Team(
    val id: String,
    val code: String,
    val name: String,
    val depot: String,
    val memberCount: Long,
)

/**
 * Строка рейтинга команд — ранжирование по среднему баллу участника, а не по суммарному (иначе
 * побеждала бы просто самая большая команда, см. javadoc `TeamService` на backend).
 */
data class TeamLeaderboardEntry(
    val rank: Long,
    val teamId: String,
    val code: String,
    val name: String,
    val depot: String,
    val memberCount: Long,
    val totalScore: Long,
    val averageScore: Double,
    val totalPlaythroughs: Long,
    val averageSafety: Double,
)
