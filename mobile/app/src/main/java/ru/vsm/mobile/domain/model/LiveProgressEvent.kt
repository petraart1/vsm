package ru.vsm.mobile.domain.model

/**
 * Снимок состояния прохождения, приходящий по живому каналу (после подключения, после
 * применённого выбора или после серверного таймаута). Поля, относящиеся только к применённому
 * выбору ([appliedChoiceId] и далее), `null` на начальном снимке при подключении.
 */
data class LiveProgressState(
    val progressId: String,
    val status: ProgressStatus,
    val loyaltyScore: Int,
    val safetyScore: Int,
    val currentNode: ScenarioNode?,
    val appliedChoiceId: String?,
    val appliedChoiceCode: String?,
    val wasTimeout: Boolean?,
    val loyaltyDelta: Int?,
    val safetyDelta: Int?,
    val finalOutcome: ScenarioOutcome?,
)

/**
 * Событие живого канала прохождения (тикер таймера, применённый выбор). Источник истины
 * по-прежнему REST — этот поток только ускоряет отклик UI, реализация вправе молча
 * переподключаться при обрыве (см. [Disconnected]/[Reconnected]).
 */
sealed interface LiveProgressEvent {
    /** Раз в секунду, пока текущий узел под активным таймером. */
    data class Tick(val progressId: String, val secondsRemaining: Int) : LiveProgressEvent

    /** Снимок при подключении либо после любого применённого выбора (REST или серверный таймаут). */
    data class State(val state: LiveProgressState) : LiveProgressEvent

    /** Сервер сам применил выбор по умолчанию узла по истечении дедлайна, без запроса клиента. */
    data class Timeout(val state: LiveProgressState) : LiveProgressEvent

    /** Прохождение завершилось — дополнительно к последнему [State]/[Timeout]. */
    data class Completed(val state: LiveProgressState) : LiveProgressEvent

    /** Соединение потеряно, реализация пытается переподключиться самостоятельно. */
    data object Disconnected : LiveProgressEvent

    /** Соединение восстановлено после [Disconnected]. */
    data object Reconnected : LiveProgressEvent
}
