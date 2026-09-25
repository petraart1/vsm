package ru.vsm.mobile.domain.fake

import java.util.UUID
import ru.vsm.mobile.domain.repository.PlayerRepository

/** В памяти процесса — playerId не переживает перезапуск приложения (в отличие от реальной реализации на DataStore). */
class FakePlayerRepository(
    private val fixedPlayerId: String = UUID.randomUUID().toString(),
) : PlayerRepository {

    override suspend fun getOrCreatePlayerId(): String = fixedPlayerId
}
