package ru.vsm.mobile.domain.repository

import ru.vsm.mobile.domain.model.CarClass
import ru.vsm.mobile.domain.model.Exam
import ru.vsm.mobile.domain.model.ScenarioProgress

/**
 * Режим экзамена: набор сценариев, пройденных подряд без подсказок по ходу (шкалы раскрываются
 * только в итоге, см. [ChoiceResult][ru.vsm.mobile.domain.model.ChoiceResult] и
 * [LiveProgressState][ru.vsm.mobile.domain.model.LiveProgressState] — их поля дельт/шкал `null`
 * для прохождений, начатых через [startCurrent]).
 */
interface ExamRepository {

    /**
     * Создаёт новый экзамен из [size] сценариев (по умолчанию на бэкенде — 10) из разных блоков
     * датасета для указанного [carClass] (по умолчанию на бэкенде — [CarClass.STANDARD]).
     */
    suspend fun start(playerId: String, carClass: CarClass? = null, size: Int? = null): Result<Exam>

    /**
     * Начинает (или возвращает уже начатое) прохождение текущего непройденного пункта экзамена.
     * Дальше — обычные методы [ScenarioRepository] ([ScenarioRepository.choose]/
     * [ScenarioRepository.timeout]/[ScenarioRepository.liveEvents]) с полученным
     * [ScenarioProgress.progressId] — экзамен не вводит отдельный протокол хода по графу.
     */
    suspend fun startCurrent(examId: String, playerId: String): Result<ScenarioProgress>

    /** Текущее состояние экзамена (для экрана прогресса и для итога после завершения). */
    suspend fun get(examId: String, playerId: String): Result<Exam>
}
