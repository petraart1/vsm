package ru.vsm.backend.ws.dto;

import java.util.UUID;
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.web.dto.NodeStateResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Единый формат событий, которые сервер шлёт в WebSocket-канал {@code /ws/progress/{progressId}}.
 * Один record на все 4 типа события ({@link #type}), поля, не относящиеся к конкретному типу,
 * остаются {@code null} и не попадают в JSON ({@link JsonInclude.Include#NON_NULL}):
 *
 * <ul>
 *   <li><b>tick</b> — раз в секунду, только для узла с активным таймером: только
 *       {@code progressId}+{@code secondsRemaining};</li>
 *   <li><b>timeout</b> — сервер сам применил {@code defaultChoice} узла по истечении дедлайна,
 *       без запроса клиента (см. {@code ru.vsm.backend.ws.ProgressChannelRegistry}): полный набор
 *       полей применённого выбора;</li>
 *   <li><b>state</b> — после любого применённого выбора (через REST или через серверный timeout
 *       выше) — та же форма, что и {@code timeout}, но по любой причине изменения;</li>
 *   <li><b>completed</b> — дополнительно к последнему {@code state}, когда прохождение перешло в
 *       {@link ProgressStatus#COMPLETED} — тем же набором полей, после него сервер закрывает
 *       WebSocket-сессии этого прохождения.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProgressWsMessage(
        String type,
        UUID progressId,
        Integer secondsRemaining,
        UUID appliedChoiceId,
        String appliedChoiceCode,
        Boolean wasTimeout,
        Integer loyaltyDelta,
        Integer safetyDelta,
        Integer loyaltyScore,
        Integer safetyScore,
        ProgressStatus status,
        ScenarioOutcome finalOutcome,
        NodeStateResponse currentNode) {

    public static final String TYPE_TICK = "tick";
    public static final String TYPE_TIMEOUT = "timeout";
    public static final String TYPE_STATE = "state";
    public static final String TYPE_COMPLETED = "completed";

    public static ProgressWsMessage tick(UUID progressId, long secondsRemaining) {
        return new ProgressWsMessage(
                TYPE_TICK, progressId, (int) secondsRemaining,
                null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Начальный снимок состояния, отправляемый сразу после успешного подключения — без
     * {@code appliedChoice*}/дельт, их ещё не было в рамках этого соединения.
     */
    public static ProgressWsMessage snapshot(UUID progressId, ProgressStatus status,
            int loyaltyScore, int safetyScore, NodeStateResponse currentNode) {
        return new ProgressWsMessage(
                TYPE_STATE, progressId, null, null, null, null,
                null, null, loyaltyScore, safetyScore, status, null, currentNode);
    }

    /**
     * {@code state}/{@code timeout}/{@code completed} share the same field set, only {@code type} differs.
     *
     * <p>{@code loyaltyDelta}/{@code safetyDelta}/{@code loyaltyScore}/{@code safetyScore} accept
     * {@code Integer} (not {@code int}) so a {@code null} from {@code ChoiceAppliedResponse} in
     * exam mode (see its Javadoc) propagates through as {@code null} instead of throwing on
     * auto-unboxing — the exam hides these fields over WebSocket the same way it hides them in REST.
     */
    public static ProgressWsMessage fromAppliedChoice(String type, UUID progressId,
            UUID appliedChoiceId, String appliedChoiceCode, boolean wasTimeout,
            Integer loyaltyDelta, Integer safetyDelta, Integer loyaltyScore, Integer safetyScore,
            ProgressStatus status, ScenarioOutcome finalOutcome, NodeStateResponse currentNode) {
        return new ProgressWsMessage(
                type, progressId, null, appliedChoiceId, appliedChoiceCode, wasTimeout,
                loyaltyDelta, safetyDelta, loyaltyScore, safetyScore, status, finalOutcome, currentNode);
    }
}
