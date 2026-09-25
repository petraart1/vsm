package ru.vsm.backend.scenario.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ru.vsm.backend.scenario.domain.NodeType;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;

/**
 * Текущий (или только что показанный) узел графа для игрока.
 *
 * <p>{@code deadlineAt} — абсолютный момент времени (сервер — источник истины), после которого
 * выбор считается просроченным и заменяется на default-выбор узла; {@code null}, если узел без
 * таймера. {@code terminalOutcome}/{@code outcomeSummary} заполнены только когда {@code terminal
 * == true}, {@code choices} в этом случае пустой список.
 */
public record NodeStateResponse(
        UUID nodeId,
        String code,
        NodeType type,
        String text,
        boolean terminal,
        Integer timerSeconds,
        Instant deadlineAt,
        ScenarioOutcome terminalOutcome,
        String outcomeSummary,
        List<ChoiceOptionResponse> choices) {
}
