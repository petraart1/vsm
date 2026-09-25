package ru.vsm.backend.scenario.web.dto;

import java.util.UUID;

/**
 * Вариант ответа, как его видит игрок ДО выбора — намеренно без {@code loyaltyDelta}/
 * {@code safetyDelta}/{@code roleSteps}: эффект на шкалы не должен раскрываться заранее.
 */
public record ChoiceOptionResponse(UUID id, String code, String text) {
}
