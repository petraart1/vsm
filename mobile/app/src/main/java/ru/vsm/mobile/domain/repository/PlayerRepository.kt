package ru.vsm.mobile.domain.repository

/**
 * Локальная идентификация игрока на устройстве — в MVP нет домена аутентификации, playerId это
 * случайный UUID, сгенерированный при первом запуске и сохранённый на устройстве (используется
 * как `X-Player-Id` в REST-запросах прохождения и как `playerId` во всех остальных доменах).
 */
interface PlayerRepository {

    /** Возвращает сохранённый playerId устройства, либо генерирует и сохраняет новый при первом вызове. */
    suspend fun getOrCreatePlayerId(): String
}
