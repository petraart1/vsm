package ru.vsm.mobile.ui.navigation

/** Именованные маршруты навграфа — единственный источник строк маршрутов для экранов. */
object Routes {
    const val TODAY = "today"
    const val SCENARIOS = "scenarios"
    const val SHIFT = "shift"
    const val AWARDS = "awards"
    const val PROFILE = "profile"
    const val LEADERBOARD = "leaderboard"
    const val NOTIFICATIONS = "notifications"
    const val CHALLENGES = "challenges"
    const val EXAM = "exam"
    const val LOGIN = "login"
    const val REGISTER = "register"

    const val PLAY_TEMPLATE = "play/{scenarioId}"
    const val DEBRIEF_TEMPLATE = "debrief/{progressId}"

    fun play(scenarioId: String) = "play/$scenarioId"
    fun debrief(progressId: String) = "debrief/$progressId"
}
