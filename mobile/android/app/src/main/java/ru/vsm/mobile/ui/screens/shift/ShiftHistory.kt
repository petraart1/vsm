package ru.vsm.mobile.ui.screens.shift

import android.content.Context

private const val PREFS = "shift_history"

/**
 * Итоги смен для наград (экран «Награды»): сколько раз подтверждён допуск, когда в последний раз
 * и была ли хоть одна смена без единой ошибки (право на повышение класса). Хранится на устройстве
 * — как и прогресс карьеры в [ShiftChapters.kt][readStory], полной истории смен не ведём, только
 * агрегаты, которых достаточно для служебных наград (см. `officialAwards`).
 */
data class ShiftHistoryStats(
    val admittedCount: Int,
    val lastAdmittedAtMillis: Long?,
    val hasUpgrade: Boolean,
)

fun readShiftHistory(context: Context): ShiftHistoryStats {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val last = prefs.getLong("lastAdmittedAt", -1L)
    return ShiftHistoryStats(
        admittedCount = prefs.getInt("admittedCount", 0),
        lastAdmittedAtMillis = if (last >= 0L) last else null,
        hasUpgrade = prefs.getBoolean("hasUpgrade", false),
    )
}

/** Вызывается по завершении смены ([ShiftViewModel.finishTrip]) — засчитывает только допущенные. */
fun recordShiftResult(context: Context, admitted: Boolean, upgrade: Boolean) {
    if (!admitted) return
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val count = prefs.getInt("admittedCount", 0)
    val hadUpgrade = prefs.getBoolean("hasUpgrade", false)
    prefs.edit()
        .putInt("admittedCount", count + 1)
        .putLong("lastAdmittedAt", System.currentTimeMillis())
        .putBoolean("hasUpgrade", hadUpgrade || upgrade)
        .apply()
}
