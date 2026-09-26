package ru.vsm.mobile.data.local

import kotlinx.coroutines.flow.Flow

/**
 * Персистентная FIFO-очередь [OfflineQueueEntry]. Реализация для продакшена — [DataStoreOfflineQueueStore]
 * (JSON в Jetpack DataStore, переживает перезапуск приложения между поездками без связи).
 */
interface OfflineQueueStore {

    /** Число элементов в очереди — источник для [ru.vsm.mobile.domain.repository.ScenarioRepository.pendingOfflineCount]. */
    val pendingCount: Flow<Int>

    /** Добавляет действие в конец очереди. */
    suspend fun enqueue(entry: OfflineQueueEntry)

    /** Первый (самый старый) элемент очереди, `null` если очередь пуста — не удаляет его. */
    suspend fun peekFirst(): OfflineQueueEntry?

    /** Удаляет первый элемент очереди (после успешной отправки или окончательного отказа). */
    suspend fun removeFirst()
}
