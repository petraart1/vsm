package ru.vsm.backend.scenario.seed;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO для десериализации seed-файла сценария из {@code classpath:scenarios/*.json}. */
@Data
@NoArgsConstructor
public class ScenarioSeedDto {

    /** Уникальный код сценария, естественный ключ (например, "boarding-no-ticket"). */
    private String code;

    /** id ситуации из dataset/scenarios/situations-index.json, для трассируемости. */
    private Integer situationRef;

    /** Блок датасета: boarding/baggage/safety/seating/comfort/catering/medical/lost_found/conflict/misc. */
    private String block;

    private String title;

    private String description;

    private boolean flagship = false;

    /** Версия контента графа, по умолчанию 1. Растёт при содержательных правках seed-файла — так */
    private int version = 1;

    /** Код узла, с которого начинается прохождение — должен существовать среди {@link #nodes}. */
    private String entryNode;

    private List<NodeSeedDto> nodes = new ArrayList<>();
}
