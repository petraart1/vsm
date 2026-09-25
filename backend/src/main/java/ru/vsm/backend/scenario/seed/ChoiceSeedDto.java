package ru.vsm.backend.scenario.seed;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Выбор внутри узла в seed-файле. См. {@link ScenarioSeedDto}.
 */
@Data
@NoArgsConstructor
public class ChoiceSeedDto {

    /** Код выбора, уникален в рамках узла (не глобально). */
    private String code;

    private String text;

    private int loyaltyDelta = 0;

    private int safetyDelta = 0;

    /** Код целевого узла в рамках того же сценария; null = конец сценария сразу после выбора. */
    private String target;

    private RoleStepsSeedDto roleSteps = new RoleStepsSeedDto();

    /** Ключ пояснения для экрана разбора, не сам текст. */
    private String explanationKey;

    private int sortOrder = 0;
}
