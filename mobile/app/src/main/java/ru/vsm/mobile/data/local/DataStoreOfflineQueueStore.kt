package ru.vsm.mobile.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.offlineQueueDataStore by preferencesDataStore(name = "offline_queue")

/**
 * [OfflineQueueStore] поверх Jetpack DataStore — хранит очередь одним JSON-массивом под одним
 * ключом (проще Room для десятков элементов очереди прохождения, без схемы/миграций).
 */
class DataStoreOfflineQueueStore(
    private val context: Context,
    private val json: Json,
) : OfflineQueueStore {

    private val queueKey = stringPreferencesKey("queue_json")
    private val serializer = ListSerializer(OfflineQueueEntry.serializer())

    override val pendingCount: Flow<Int> =
        context.offlineQueueDataStore.data.map { prefs -> decode(prefs[queueKey]).size }

    override suspend fun enqueue(entry: OfflineQueueEntry) {
        context.offlineQueueDataStore.edit { prefs ->
            val current = decode(prefs[queueKey])
            prefs[queueKey] = json.encodeToString(serializer, current + entry)
        }
    }

    override suspend fun peekFirst(): OfflineQueueEntry? {
        val raw = context.offlineQueueDataStore.data.first()[queueKey]
        return decode(raw).firstOrNull()
    }

    override suspend fun removeFirst() {
        context.offlineQueueDataStore.edit { prefs ->
            val current = decode(prefs[queueKey])
            if (current.isNotEmpty()) {
                prefs[queueKey] = json.encodeToString(serializer, current.drop(1))
            }
        }
    }

    private fun decode(raw: String?): List<OfflineQueueEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }
}
