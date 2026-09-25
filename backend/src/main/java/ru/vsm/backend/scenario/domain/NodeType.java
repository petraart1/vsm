package ru.vsm.backend.scenario.domain;

/**
 * Тип узла графа сценария.
 * <p>Согласовано со CHECK-констрейнтом {@code chk_scenario_nodes_type} в
 * {@code db/changelog/scenario/002-scenario-nodes.yaml}.
 */
public enum NodeType {
    /** Обычная реплика/ситуация с выбором ответа. */
    DIALOGUE,
    /** Узел эскалации (вызов начальника поезда, ПТБ, медиков и т.п.). */
    ESCALATION,
    /** Терминальный узел — конец ветки с исходом. */
    TERMINAL
}
