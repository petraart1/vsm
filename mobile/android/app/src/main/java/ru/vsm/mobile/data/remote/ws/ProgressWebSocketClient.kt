package ru.vsm.mobile.data.remote.ws

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import ru.vsm.mobile.data.mapper.toDomainEvent
import ru.vsm.mobile.data.remote.dto.ProgressWsMessageDto
import ru.vsm.mobile.domain.model.LiveProgressEvent

/**
 * Клиент живого канала `/ws/progress/{progressId}?playerId=`. REST остаётся источником истины —
 * этот клиент только ускоряет отклик UI; при обрыве соединения переподключается сам с backoff,
 * эмитя [LiveProgressEvent.Disconnected]/[LiveProgressEvent.Reconnected] вместо падения потока.
 * Нет протокола клиент->сервер — сообщения только читаются.
 */
class ProgressWebSocketClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val baseUrl: String,
) {
    /**
     * @param token опциональный JWT учётной записи — если задан, отправляется как `?token=` вместе
     *   с `playerId`; backend приоритезирует токен и откатывается на `playerId` сам при
     *   невалидном/просроченном токене (см. `ProgressWebSocketHandler.extractPlayerId`), поэтому
     *   отправлять оба параметра всегда безопасно.
     */
    fun observe(progressId: String, playerId: String, token: String? = null): Flow<LiveProgressEvent> = callbackFlow {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("ws/progress/$progressId")
            .addQueryParameter("playerId", playerId)
            .apply { if (!token.isNullOrBlank()) addQueryParameter("token", token) }
            .build()
        val request = Request.Builder().url(url).build()

        var attempt = 0
        var socket: WebSocket? = null
        var reconnectJob: Job? = null

        fun connect() {
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (attempt > 0) {
                        trySend(LiveProgressEvent.Reconnected)
                    }
                    attempt = 0
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val dto = runCatching {
                        json.decodeFromString(ProgressWsMessageDto.serializer(), text)
                    }.getOrNull() ?: return
                    val event = dto.toDomainEvent() ?: return
                    trySend(event)
                    if (event is LiveProgressEvent.Completed) {
                        webSocket.close(NORMAL_CLOSURE_CODE, "progress_completed")
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (code != NORMAL_CLOSURE_CODE) {
                        scheduleReconnect()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    scheduleReconnect()
                }

                fun scheduleReconnect() {
                    trySend(LiveProgressEvent.Disconnected)
                    attempt++
                    val delayMs = minOf(1000L shl minOf(attempt, 4), MAX_RECONNECT_DELAY_MS)
                    reconnectJob = launch {
                        kotlinx.coroutines.delay(delayMs)
                        connect()
                    }
                }
            }
            socket = okHttpClient.newWebSocket(request, listener)
        }

        connect()

        awaitClose {
            reconnectJob?.cancel()
            socket?.close(NORMAL_CLOSURE_CODE, "client_closed")
        }
    }

    private companion object {
        const val NORMAL_CLOSURE_CODE = 1000
        const val MAX_RECONNECT_DELAY_MS = 16_000L
    }
}
