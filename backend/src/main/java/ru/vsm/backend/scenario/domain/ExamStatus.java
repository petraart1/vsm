package ru.vsm.backend.scenario.domain;

/**
 * Статус экзамена целиком (не отдельного сценария в его составе — см. {@link ExamScenario#isCompleted()}).
 * <p>Согласовано со CHECK-констрейнтом {@code chk_exam_status}.
 */
public enum ExamStatus {
    IN_PROGRESS,
    COMPLETED
}
