package ru.vsm.backend.feedback.dto;

import java.util.List;
import ru.vsm.backend.scenario.domain.NodeType;

/** Один шаг таймлайна разбора прохождения (см. {@code design/screens/debrief.md}, раздел */
public record DebriefStepDto(
        int sequenceIndex,
        String nodeCode,
        String nodeText,
        NodeType nodeType,
        String choiceCode,
        String choiceText,
        boolean wasTimeout,
        int loyaltyDelta,
        int safetyDelta,
        List<String> roleStepsCompleted,
        List<String> roleStepsSkipped,
        boolean scaleConflict,
        String explanation,
        boolean hiddenCommunicationEffect,
        String normRef) {
}
