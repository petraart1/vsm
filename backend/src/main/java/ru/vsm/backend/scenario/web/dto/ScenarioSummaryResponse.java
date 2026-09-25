package ru.vsm.backend.scenario.web.dto;

import java.util.UUID;

/** Элемент каталога сценариев (для списка/детали) — без графа узлов. */
public record ScenarioSummaryResponse(
        UUID id,
        String code,
        Integer situationRef,
        String block,
        String title,
        String description,
        boolean flagship) {
}
