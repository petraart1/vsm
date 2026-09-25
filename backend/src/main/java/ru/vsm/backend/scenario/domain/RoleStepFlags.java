package ru.vsm.backend.scenario.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Пометки соблюдения шагов универсальной ролевой модели ответа проводника
 * (см. {@code dataset/scenarios/situations-onboard.md}, раздел "Универсальная ролевая модель"):
 * признать ситуацию -> обозначить правило -> предложить решение -> заверить.
 * <p>Пропуск шага может снижать лояльность даже при формально верном итоговом решении —
 * эти флаги используются для разбора решений игрока.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class RoleStepFlags {

    @Column(name = "role_step_acknowledge", nullable = false)
    @Builder.Default
    private boolean acknowledge = false;

    @Column(name = "role_step_rule", nullable = false)
    @Builder.Default
    private boolean rule = false;

    @Column(name = "role_step_solution", nullable = false)
    @Builder.Default
    private boolean solution = false;

    @Column(name = "role_step_reassure", nullable = false)
    @Builder.Default
    private boolean reassure = false;
}
