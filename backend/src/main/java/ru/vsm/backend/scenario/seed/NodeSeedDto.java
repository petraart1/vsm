package ru.vsm.backend.scenario.seed;

import java.util.ArrayList;
import java.util.List;
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

    /** SUCCESS | PARTIAL | FAILURE, только если {@link #terminal} = true. */
    private String terminalOutcome;

    private String outcomeSummary;

    private List<ChoiceSeedDto> choices = new ArrayList<>();
}
