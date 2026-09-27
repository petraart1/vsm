package ru.vsm.mobile.ui.screens.today

import android.content.Context
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import ru.vsm.mobile.ui.components.StreakDay

private const val PREFS_NAME = "vsm.streak"
private const val KEY_DATES = "open_dates"

/** Серия входов в приложение хранится на устройстве (по датам открытия «Сегодня»). */
data class StreakInfo(val streakDays: Int, val week: List<StreakDay>)

/**
 * Локальная серия входов — аналог веб-версии, которая считает её по истории смен в хранилище
 * браузера. У мобильного клиента нет отдельного backend-эндпоинта для этого счётчика, поэтому
 * серия считается по датам открытия экрана «Сегодня» на этом устройстве.
 */
object TodayStreak {

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Отмечает сегодняшний день как день захода и возвращает обновлённую серию для отрисовки. */
    fun recordOpenToday(context: Context): StreakInfo {
        val store = prefs(context)
        val today = LocalDate.now()
        val stored = store.getString(KEY_DATES, "").orEmpty()
        val dates = stored.split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toMutableSet()
        dates.add(today)
        val cutoff = today.minusDays(60)
        val trimmed = dates.filter { !it.isBefore(cutoff) }.toSet()
        store.edit().putString(KEY_DATES, trimmed.joinToString(",") { it.toString() }).apply()
        return computeStreak(trimmed, today)
    }

    /** Сбрасывает локально накопленную серию входов (кнопка «Очистить данные на устройстве» в настройках). */
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_DATES).apply()
    }

    private fun computeStreak(dates: Set<LocalDate>, today: LocalDate): StreakInfo {
        var streak = 0
        var cursor = today
        while (dates.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        val week = (6 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            StreakDay(
                label = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("ru")).uppercase(Locale("ru")),
                done = dates.contains(day),
                today = day == today,
                future = day.isAfter(today),
            )
        }
        return StreakInfo(streakDays = streak, week = week)
    }
}
