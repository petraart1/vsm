package ru.vsm.mobile.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.vsm.mobile.domain.model.ChoiceOutcome
import ru.vsm.mobile.domain.model.ChoiceResult
import ru.vsm.mobile.domain.model.LiveProgressEvent
import ru.vsm.mobile.domain.model.ScenarioProgress
import ru.vsm.mobile.domain.model.ScenarioSummary

/** Каталог сценариев и прохождение (граф узлов, таймеры, шкалы лояльности/безопасности). */
interface ScenarioRepository {

    /** Список сценариев, опционально отфильтрованный по блоку ситуаций (boarding/medical/...). */
    suspend fun list(block: String? = null): Result<List<ScenarioSummary>>

    /** Начинает новое прохождение сценария для игрока. */
    suspend fun start(scenarioId: String, playerId: String): Result<ScenarioProgress>

    /** Текущее состояние уже начатого прохождения (для восстановления экрана после разрыва). */
    suspend fun getProgress(progressId: String, playerId: String): Result<ScenarioProgress>

    /** Делает выбор в текущем узле прохождения. */
    suspend fun choose(progressId: String, choiceId: String, playerId: String): Result<ChoiceResult>

    /**
     * Явный запрос клиента "время вышло" — применяет выбор по умолчанию текущего узла.
     * Нужен как резервный путь, если живой канал ([liveEvents]) недоступен и таймер истёк
     * без серверного push-события.
     */
    suspend fun timeout(progressId: String, playerId: String): Result<ChoiceResult>

    /**
     * Живой канал таймера и шкал для уже начатого прохождения. REST остаётся источником истины:
     * поток можно не собирать вовсе, тогда состояние обновляется только через [getProgress]/
     * [choose]/[timeout]. Реализация переподключается самостоятельно при обрыве соединения
     * (см. [LiveProgressEvent.Disconnected]/[LiveProgressEvent.Reconnected]) и не бросает
     * исключение при сетевых сбоях — только эмитит события.
     *
     * @param token опциональный токен учётной записи ([AuthRepository]) — если задан, передаётся
     *   на канал вместе с [playerId] (backend приоритезирует токен, откатывается на [playerId]
     *   при невалидном/просроченном токене). `null` — обычный анонимный путь, как раньше.
     */
    fun liveEvents(progressId: String, playerId: String, token: String? = null): Flow<LiveProgressEvent>

    /**
     * Офлайн-устойчивый вариант [choose]: связь в поезде может пропадать в разгар прохождения.
     * При сетевой ошибке ([ru.vsm.mobile.domain.error.DomainError.Network]) действие сохраняется в
     * локальную очередь и отправляется по восстановлении сети (см. [pendingOfflineCount]) — метод
     * при этом возвращает `Result.success` с [ChoiceOutcome.QueuedOffline], а не `Result.failure`.
     * Любая другая ошибка (HTTP 4xx/5xx, разбор ответа) проксируется как обычно через
     * `Result.failure`, поведение не отличается от [choose]. [choose] по-прежнему доступен для
     * вызывающего кода, которому офлайн-очередь не нужна.
     */
    suspend fun chooseOrQueue(progressId: String, choiceId: String, playerId: String): Result<ChoiceOutcome>

    /** Офлайн-устойчивый вариант [timeout] — см. [chooseOrQueue]. */
    suspend fun timeoutOrQueue(progressId: String, playerId: String): Result<ChoiceOutcome>

    /**
     * Количество ещё не отправленных действий в локальной офлайн-очереди (для индикатора на
     * экране прохождения — "N действий будет отправлено при восстановлении связи"). Эмитит `0`,
     * если очередь пуста. Обновляется как при постановке в очередь ([chooseOrQueue]/[timeoutOrQueue]),
     * так и при успешной/отброшенной отправке накопленных действий.
     */
    fun pendingOfflineCount(): Flow<Int>
}
