package ru.vsm.backend.feedback.dto;

/** Как часто игрок соблюдает/пропускает один шаг универсальной ролевой модели ответа */
public record RoleStepComplianceDto(
        RoleStep step,
        String stepLabel,
        int timesFollowed,
        int timesSkipped,
        double complianceRate) {
}
