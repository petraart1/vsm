package ru.vsm.backend.ws;

import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import ru.vsm.backend.auth.security.JwtClaims;
import ru.vsm.backend.auth.security.JwtService;
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.service.ScenarioPlayService;
import ru.vsm.backend.scenario.service.exception.ProgressAccessDeniedException;
import ru.vsm.backend.scenario.service.exception.ProgressNotFoundException;
import ru.vsm.backend.scenario.web.dto.ProgressStateResponse;
import ru.vsm.backend.ws.dto.ProgressWsMessage;

/**
 * Обычный WebSocket (не STOMP) на {@code /ws/progress/{progressId}?playerId=<uuid>} — живой
 * таймер и шкалы для уже начатого прохождения. Владение проверяется тем же способом, что и в
 * REST ({@code ScenarioPlayService.getProgress}, тот же {@code playerId}), поэтому чужое
 * прохождение по WebSocket недоступно так же, как и по REST.
 *
 * <p>{@code ?token=<jwt>} — альтернатива {@code playerId} для авторизованных клиентов (тот же
 * токен, что выдаёт {@code POST /api/auth/login}): playerId берётся из токена, query-параметр
 * {@code playerId} в этом случае игнорируется. Невалидный/просроченный токен равносилен его
 * отсутствию — сервер откатывается на {@code playerId}, а если и его нет, закрывает соединение.
 *
 * <p>Клиент не обязан ничего слать — сообщения от клиента игнорируются (нет клиент→сервер
 * протокола); подключение только читает события. Формат событий — {@link ProgressWsMessage}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgressWebSocketHandler extends TextWebSocketHandler {

    private static final String PLAYER_ID_QUERY_PARAM = "playerId";
    private static final String TOKEN_QUERY_PARAM = "token";
    private static final String PROGRESS_ID_ATTRIBUTE = "progressId";

    private final ScenarioPlayService scenarioPlayService;
    private final ProgressChannelRegistry registry;
    private final JwtService jwtService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID progressId = extractProgressId(session);
        UUID playerId = extractPlayerId(session);
        if (progressId == null || playerId == null) {
            closeQuietly(session, CloseStatus.BAD_DATA.withReason("progressId (путь) и playerId (query) обязательны"));
            return;
        }

        ProgressStateResponse state;
        try {
            state = scenarioPlayService.getProgress(progressId, playerId);
        } catch (ProgressNotFoundException | ProgressAccessDeniedException e) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION.withReason("progress_not_accessible"));
            return;
        } catch (RuntimeException e) {
            log.warn("Ошибка при подключении WebSocket к прохождению {}: {}", progressId, e.getMessage(), e);
            closeQuietly(session, CloseStatus.SERVER_ERROR);
            return;
        }

        session.getAttributes().put(PROGRESS_ID_ATTRIBUTE, progressId);
        registry.register(progressId, playerId, session);
        registry.sendTo(session, ProgressWsMessage.snapshot(
                progressId, state.status(), state.loyaltyScore(), state.safetyScore(), state.currentNode()));

        if (state.status() == ProgressStatus.IN_PROGRESS
                && state.currentNode() != null && state.currentNode().deadlineAt() != null) {
            registry.scheduleTicker(progressId, playerId, state.currentNode().deadlineAt());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object progressId = session.getAttributes().get(PROGRESS_ID_ATTRIBUTE);
        if (progressId instanceof UUID id) {
            registry.unregister(id, session);
        }
    }

    private UUID extractProgressId(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        String path = session.getUri().getPath();
        String[] segments = path.split("/");
        return segments.length > 0 ? parseUuid(segments[segments.length - 1]) : null;
    }

    private UUID extractPlayerId(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        var queryParams = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams();
        String token = queryParams.getFirst(TOKEN_QUERY_PARAM);
        if (token != null && !token.isBlank()) {
            UUID playerIdFromToken = jwtService.parse(token).map(JwtClaims::playerId).orElse(null);
            if (playerIdFromToken != null) {
                return playerIdFromToken;
            }
            log.debug("Невалидный/просроченный WS-токен, откатываемся на query-параметр {}", PLAYER_ID_QUERY_PARAM);
        }
        return parseUuid(queryParams.getFirst(PLAYER_ID_QUERY_PARAM));
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            log.debug("Не удалось закрыть WebSocket-сессию {}: {}", session.getId(), e.getMessage());
        }
    }
}
