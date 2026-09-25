package ru.vsm.backend.scenario.domain;

/**
 * Итог терминального узла или прохождения сценария целиком.
 * <p>Согласовано со CHECK-констрейнтами {@code chk_scenario_nodes_outcome} и
 * {@code chk_user_progress_outcome}.
 */
public enum ScenarioOutcome {
    SUCCESS,
    PARTIAL,
    FAILURE
}
