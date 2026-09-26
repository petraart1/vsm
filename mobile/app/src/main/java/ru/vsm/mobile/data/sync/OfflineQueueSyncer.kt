package ru.vsm.mobile.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import ru.vsm.mobile.data.local.OfflineQueueEntry
import ru.vsm.mobile.data.local.OfflineQueueStore
import ru.vsm.mobile.data.mapper.toDomain
import ru.vsm.mobile.data.remote.SafeApiCall
import ru.vsm.mobile.data.remote.api.ScenarioApi
import ru.vsm.mobile.domain.error.DomainError
import ru.vsm.mobile.domain.error.isProgressAlreadyCompleted
import ru.vsm.mobile.domain.model.ChoiceResult

/** Итог попытки отправить один элемент [OfflineQueueEntry] на backend. */
sealed class QueueSendOutcome {
    data class Delivered(val result: ChoiceResult) : QueueSendOutcome()

    /** 409 `progress_already_completed` — backend идемпотентен, действие уже применено раньше. */
    object AlreadyApplied : QueueSendOutcome()

    /** Любой другой 4xx/5xx (не сеть) — действие больше не актуально, отбрасывается с логом. */
    data class Rejected(val error: DomainError.Api) : QueueSendOutcome()

    /** Не дошло до сервера — оставить в очереди и повторить позже. */
    data class NetworkFailure(val error: DomainError.Network) : QueueSendOutcome()

    /** Не сеть и не HTTP-код (например, не разобрался ответ) — отбрасывается, чтобы не зависнуть навсегда. */
    data class UnexpectedFailure(val error: DomainError) : QueueSendOutcome()
}

/**
 * Отправляет накопленную офлайн-очередь `choose`/`timeout` по порядку (FIFO) при восстановлении
 * сети и сразу при создании (старт приложения). Backend идемпотентен для уже применённого выбора
 * (409 `progress_already_completed`) — такой ответ считается доставкой, а не ошибкой.
 *
 * Один экземпляр на процесс приложения, живёт всё время жизни [scope] (см. `AppContainer`).
 */
class OfflineQueueSyncer(
    private val queueStore: OfflineQueueStore,
    private val api: ScenarioApi,
    private val safeApiCall: SafeApiCall,
    isOnline: Flow<Boolean>,
    scope: CoroutineScope,
) {
    private val trigger = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (ignored in trigger) {
                flushWithBackoff()
            }
        }
        scope.launch {
            isOnline.collect { online -> if (online) requestFlush() }
        }
        requestFlush()
    }

    /** Просит проверить очередь сейчас (например, экран прохождения только что поставил элемент в неё). */
    fun requestFlush() {
        trigger.trySend(Unit)
    }

    private suspend fun flushWithBackoff() {
        var backoffMs = INITIAL_BACKOFF_MS
        while (!flushQueueOnce()) {
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    /**
     * Отправляет по порядку (FIFO) все элементы очереди, пока она не опустеет, либо пока не
     * встретится сетевая ошибка (тогда останавливается, не трогая оставшиеся элементы — их
     * отправит следующая попытка). Возвращает `true`, если очередь в итоге опустела. Публичный —
     * не полагается на фоновый `Channel`/`scope`, тесты вызывают и дожидаются напрямую.
     */
    suspend fun flushQueueOnce(): Boolean {
        while (true) {
            val head = queueStore.peekFirst() ?: return true
            when (sendOne(head)) {
                is QueueSendOutcome.NetworkFailure -> return false
                else -> {
                    // Delivered / AlreadyApplied / Rejected / UnexpectedFailure — во всех случаях
                    // элемент обработан (успешно или окончательно отброшен) и не должен отправляться повторно.
                    queueStore.removeFirst()
                }
            }
        }
    }

    /** Одна попытка отправки без побочных эффектов на очередь — используется [flushQueueOnce] и тестами напрямую. */
    suspend fun sendOne(entry: OfflineQueueEntry): QueueSendOutcome {
        val result = safeApiCall.call {
            if (entry.choiceId != null) {
                api.choose(entry.progressId, entry.choiceId, entry.playerId).toDomain()
            } else {
                api.timeout(entry.progressId, entry.playerId).toDomain()
            }
        }
        return result.fold(
            onSuccess = { QueueSendOutcome.Delivered(it) },
            onFailure = { error ->
                when {
                    error is DomainError.Network -> QueueSendOutcome.NetworkFailure(error)
                    error is DomainError.Api && error.isProgressAlreadyCompleted() -> QueueSendOutcome.AlreadyApplied
                    error is DomainError.Api -> QueueSendOutcome.Rejected(error)
                    error is DomainError -> QueueSendOutcome.UnexpectedFailure(error)
                    else -> QueueSendOutcome.UnexpectedFailure(DomainError.Unexpected(error))
                }
            },
        )
    }

    private companion object {
        const val INITIAL_BACKOFF_MS = 1000L
        const val MAX_BACKOFF_MS = 16_000L
    }
}
