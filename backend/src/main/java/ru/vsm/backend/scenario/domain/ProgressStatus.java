package ru.vsm.backend.scenario.domain;

/**
 * Статус прохождения сценария игроком.
 * <p>Согласовано со CHECK-констрейнтом {@code chk_user_progress_status}.
 */
public enum ProgressStatus {
    IN_PROGRESS,
    COMPLETED,
    ABANDONED
}
