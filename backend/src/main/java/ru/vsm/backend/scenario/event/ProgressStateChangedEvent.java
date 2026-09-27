package ru.vsm.backend.scenario.event;

import java.util.UUID;
import ru.vsm.backend.scenario.web.dto.ChoiceAppliedResponse;

/** Доменное событие: состояние прохождения изменилось в результате применённого выбора — */
public record ProgressStateChangedEvent(UUID playerId, ChoiceAppliedResponse appliedChoice) {
}
