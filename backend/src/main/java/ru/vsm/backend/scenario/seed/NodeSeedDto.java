package ru.vsm.backend.scenario.seed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Узел графа в seed-файле. См. {@link ScenarioSeedDto}.
 */
@Data
@NoArgsConstructor
public class NodeSeedDto {

    /** Код узла, уникален в рамках сценария (не глобально). */
    private String code;

    /** DIALOGUE | ESCALATION | TERMINAL — см. {@link ru.vsm.backend.scenario.domain.NodeType}. */
    private String type;

    private String text;

    /** null = без таймера. */
    private Integer timerSeconds;

    /** Код выбора (среди {@link #choices} этого же узла), применяемый при истечении таймера. */
    private String defaultChoice;

    private boolean terminal = false;

    /**
     * Узел закадровой коммуникации (например, разговор по служебной рации) — пассажир решение не
     * слышит и не видит. См. {@link ru.vsm.backend.scenario.domain.ScenarioNode#isHiddenFromPassenger()}.
     */
    private boolean hiddenFromPassenger = false;

    /** SUCCESS | PARTIAL | FAILURE, только если {@link #terminal} = true. */
    private String terminalOutcome;

    private String outcomeSummary;

    /**
     * «Портрет пассажира»: переопределение {@link #text} по классу вагона (ключ — имя
     * {@link ru.vsm.backend.scenario.domain.CarClass}, например {@code "FIRST"}). Опционально —
     * если для класса записи нет, используется общий {@link #text}. См. javadoc
     * {@link ru.vsm.backend.scenario.domain.ScenarioNodePortrait}.
     */
    private Map<String, String> passengerPortraits = new LinkedHashMap<>();

    private List<ChoiceSeedDto> choices = new ArrayList<>();
}
