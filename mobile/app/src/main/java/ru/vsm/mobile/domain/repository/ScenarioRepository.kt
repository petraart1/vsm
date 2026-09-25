package ru.vsm.mobile.domain.repository

import kotlinx.coroutines.flow.Flow
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
     */
    fun liveEvents(progressId: String, playerId: String): Flow<LiveProgressEvent>
}
