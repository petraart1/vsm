package ru.vsm.mobile.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory [OfflineQueueStore] для тестов — без DataStore/Context. */
class FakeOfflineQueueStore : OfflineQueueStore {

    private val state = MutableStateFlow<List<OfflineQueueEntry>>(emptyList())
    private val countState = MutableStateFlow(0)

    /** Ковариантно переопределяет `Flow<Int>` из интерфейса — тесты читают `.value` синхронно. */
    override val pendingCount: StateFlow<Int> get() = countState

    override suspend fun enqueue(entry: OfflineQueueEntry) {
        state.value = state.value + entry
        countState.value = state.value.size
    }

    override suspend fun peekFirst(): OfflineQueueEntry? = state.value.firstOrNull()

    override suspend fun removeFirst() {
        state.value = state.value.drop(1)
        countState.value = state.value.size
    }

    fun snapshot(): List<OfflineQueueEntry> = state.value
}
