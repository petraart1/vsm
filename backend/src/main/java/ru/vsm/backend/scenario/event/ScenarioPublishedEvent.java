package ru.vsm.backend.scenario.event;

import java.time.Instant;
import java.util.UUID;

/** Доменное событие: в каталоге появился новый сценарий (создан через редактор сценариев, */
public record ScenarioPublishedEvent(
        UUID scenarioId,
        String scenarioCode,
        String title,
        String block,
        Instant publishedAt) {
}
