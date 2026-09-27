package ru.vsm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `ru.vsm.backend.ws.dto.ProgressWsMessage` — единый формат событий `/ws/progress/{progressId}`.
 * Сервер сериализует с `@JsonInclude(NON_NULL)`, поэтому поля, не относящиеся к конкретному
 * [type], отсутствуют в JSON — все опциональные поля здесь должны иметь дефолт `null`.
 */
@Serializable
data class ProgressWsMessageDto(
    val type: String,
    val progressId: String,
    val secondsRemaining: Int? = null,
    val appliedChoiceId: String? = null,
    val appliedChoiceCode: String? = null,
    val wasTimeout: Boolean? = null,
    val loyaltyDelta: Int? = null,
    val safetyDelta: Int? = null,
    val loyaltyScore: Int? = null,
    val safetyScore: Int? = null,
    val status: ProgressStatusDto? = null,
    val finalOutcome: ScenarioOutcomeDto? = null,
    val currentNode: NodeStateDto? = null,
) {
    companion object {
        const val TYPE_TICK = "tick"
        const val TYPE_TIMEOUT = "timeout"
        const val TYPE_STATE = "state"
        const val TYPE_COMPLETED = "completed"
    }
}
