package ru.vsm.mobile.data.mapper

import ru.vsm.mobile.data.remote.dto.ProgressWsMessageDto
import ru.vsm.mobile.domain.model.LiveProgressEvent
import ru.vsm.mobile.domain.model.LiveProgressState

// loyaltyScore/safetyScore не requireNotNull — в режиме экзамена сервер не присылает их вовсе
// (см. javadoc ProgressWsMessage.fromAppliedChoice на бэкенде), это не ошибка сообщения.
private fun ProgressWsMessageDto.toState(): LiveProgressState = LiveProgressState(
    progressId = progressId,
    status = requireNotNull(status?.toDomain()) { "WS-сообщение type=$type без status: $this" },
    loyaltyScore = loyaltyScore,
    safetyScore = safetyScore,
    currentNode = currentNode?.toDomain(),
    appliedChoiceId = appliedChoiceId,
    appliedChoiceCode = appliedChoiceCode,
    wasTimeout = wasTimeout,
    loyaltyDelta = loyaltyDelta,
    safetyDelta = safetyDelta,
    finalOutcome = finalOutcome?.toDomain(),
)

/**
 * `null`, если сообщение неизвестного/непредвиденного `type` — вызывающий код (WS-клиент)
 * такие сообщения молча пропускает, не обрывая соединение.
 */
fun ProgressWsMessageDto.toDomainEvent(): LiveProgressEvent? = when (type) {
    ProgressWsMessageDto.TYPE_TICK ->
        LiveProgressEvent.Tick(progressId, requireNotNull(secondsRemaining) { "tick без secondsRemaining: $this" })
    ProgressWsMessageDto.TYPE_STATE -> LiveProgressEvent.State(toState())
    ProgressWsMessageDto.TYPE_TIMEOUT -> LiveProgressEvent.Timeout(toState())
    ProgressWsMessageDto.TYPE_COMPLETED -> LiveProgressEvent.Completed(toState())
    else -> null
}
