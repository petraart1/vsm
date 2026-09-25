package ru.vsm.backend.feedback.dto;

/**
 * Как часто игрок соблюдает/пропускает один шаг универсальной ролевой модели ответа
 * (см. {@link RoleStep}) по всем выборам во всех завершённых прохождениях.
 *
 * @param step          шаг ролевой модели
 * @param stepLabel     человекочитаемая подпись ({@link RoleStep#label()}), чтобы фронту не
 *                      дублировать словарь enum -> подпись
 * @param timesFollowed сколько сделанных выборов отмечали этот шаг
 * @param timesSkipped  сколько сделанных выборов пропускали этот шаг
 * @param complianceRate timesFollowed / (timesFollowed + timesSkipped), 0, если выборов не было
 */
public record RoleStepComplianceDto(
        RoleStep step,
        String stepLabel,
        int timesFollowed,
        int timesSkipped,
        double complianceRate) {
}
