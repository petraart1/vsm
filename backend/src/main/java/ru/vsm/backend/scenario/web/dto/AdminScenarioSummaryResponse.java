package ru.vsm.backend.scenario.web.dto;

import ru.vsm.backend.scenario.domain.Scenario;

/**
 * Элемент административного списка сценариев ({@code GET /api/admin/scenarios}) — в отличие от
 * {@link ScenarioSummaryResponse} каталога включает неактивные сценарии и служебные поля
 * ({@code active}, {@code version}), которые игроку не нужны.
 */
public record AdminScenarioSummaryResponse(
        String code, String title, String block, boolean flagship, boolean active, int version) {

    public static AdminScenarioSummaryResponse from(Scenario s) {
        return new AdminScenarioSummaryResponse(
                s.getCode(), s.getTitle(), s.getBlock(), s.isFlagship(), s.isActive(), s.getVersion());
    }
}
