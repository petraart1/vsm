package ru.vsm.mobile.data.local

import kotlinx.serialization.Serializable

/**
 * Одно отложенное действие прохождения (`choose`/`timeout`), которое не удалось отправить сразу
 * из-за отсутствия сети. [choiceId] — `null` для отложенного `timeout`. Порядок в очереди —
 * порядок постановки (FIFO), отправляется в том же порядке при восстановлении связи.
 */
@Serializable
data class OfflineQueueEntry(
    val id: String,
    val progressId: String,
    val playerId: String,
    val choiceId: String?,
    val queuedAt: String,
)
