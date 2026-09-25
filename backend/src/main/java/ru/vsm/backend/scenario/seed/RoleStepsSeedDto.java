package ru.vsm.backend.scenario.seed;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Пометки шагов универсальной ролевой модели для выбора в seed-файле:
 * признать ситуацию -> обозначить правило -> предложить решение -> заверить.
 * Поля, отсутствующие в JSON, по умолчанию {@code false} (шаг не соблюдён).
 */
@Data
@NoArgsConstructor
public class RoleStepsSeedDto {

    private boolean acknowledge = false;
    private boolean rule = false;
    private boolean solution = false;
    private boolean reassure = false;
}
