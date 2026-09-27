package ru.vsm.backend.ws.dto;

import java.util.UUID;
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.web.dto.NodeStateResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Единый формат событий, которые сервер шлёт в WebSocket-канал {@code /ws/progress/{progressId}}. */
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

    /** exam mode (see its Javadoc) propagates through as {@code null} instead of throwing on */
    public static ProgressWsMessage fromAppliedChoice(String type, UUID progressId,
            UUID appliedChoiceId, String appliedChoiceCode, boolean wasTimeout,
            Integer loyaltyDelta, Integer safetyDelta, Integer loyaltyScore, Integer safetyScore,
            ProgressStatus status, ScenarioOutcome finalOutcome, NodeStateResponse currentNode) {
        return new ProgressWsMessage(
                type, progressId, null, appliedChoiceId, appliedChoiceCode, wasTimeout,
                loyaltyDelta, safetyDelta, loyaltyScore, safetyScore, status, finalOutcome, currentNode);
    }
}
