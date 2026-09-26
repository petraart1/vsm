package ru.vsm.mobile.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.authDataStore by preferencesDataStore(name = "auth")

/**
 * [AuthSessionStore] поверх Jetpack DataStore — хранит сессию одним JSON-объектом под одним
 * ключом (тот же приём, что у [DataStoreOfflineQueueStore]). Открытый текст, без шифрования —
 * приемлемо для хакатон-MVP; на реальном проде — `EncryptedSharedPreferences`/DataStore+Tink.
 */
class DataStoreAuthSessionStore(
    private val context: Context,
    private val json: Json,
) : AuthSessionStore {

    private val sessionKey = stringPreferencesKey("session_json")

    override val session: Flow<AuthSession?> =
        context.authDataStore.data.map { prefs -> decode(prefs[sessionKey]) }

    override suspend fun getToken(): String? =
        decode(context.authDataStore.data.first()[sessionKey])?.token

    override suspend fun save(session: AuthSession) {
        context.authDataStore.edit { prefs -> prefs[sessionKey] = json.encodeToString(AuthSession.serializer(), session) }
    }

    override suspend fun clear() {
        context.authDataStore.edit { prefs -> prefs.remove(sessionKey) }
    }

    private fun decode(raw: String?): AuthSession? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(AuthSession.serializer(), raw) }.getOrNull()
    }
}
