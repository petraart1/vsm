package ru.vsm.mobile.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.first
import ru.vsm.mobile.domain.repository.PlayerRepository

private val Context.playerDataStore by preferencesDataStore(name = "player")

/**
 * playerId — случайный UUID, сгенерированный на устройстве при первом запуске и сохранённый в
 * DataStore (переживает перезапуск приложения, в отличие от [ru.vsm.mobile.domain.fake.FakePlayerRepository]).
 */
class PlayerIdDataStore(private val context: Context) : PlayerRepository {

    private val playerIdKey = stringPreferencesKey("player_id")

    override suspend fun getOrCreatePlayerId(): String {
        val existing = context.playerDataStore.data.first()[playerIdKey]
        if (existing != null) {
            return existing
        }
        val generated = UUID.randomUUID().toString()
        context.playerDataStore.edit { prefs -> prefs[playerIdKey] = generated }
        return generated
    }
}
