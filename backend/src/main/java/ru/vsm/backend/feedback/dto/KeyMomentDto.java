package ru.vsm.backend.feedback.dto;

/** Блок "Что можно было сделать иначе" (см. {@code design/screens/debrief.md}): ключевая развилка, */
public record KeyMomentDto(
        int sequenceIndex,
        String nodeText,
        String chosenChoiceText,
        int chosenLoyaltyDelta,
        int chosenSafetyDelta,
        String betterChoiceText,
        int betterLoyaltyDelta,
        int betterSafetyDelta,
        String adviceText,
        String betterExplanation) {
}
