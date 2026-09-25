package ru.vsm.backend.scenario.seed;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для десериализации seed-файла сценария из {@code classpath:scenarios/*.json}.
 *
 * <p>Один файл = один сценарий с полным графом узлов и выборов, ключ идемпотентности — {@link #code}.
 * Обычные POJO с Lombok (не record) — так безопаснее для Jackson-биндинга без явных {@code @JsonCreator}.
 */
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

    /**
     * Версия контента графа, по умолчанию 1. Растёт при содержательных правках seed-файла — так
     * {@code ScenarioSeedService} узнаёт, что уже загруженный сценарий с тем же {@link #code} нужно
     * перезаписать (см. javadoc {@code ScenarioSeedService.seed}), а не просто пропустить.
     */
    private int version = 1;

    /** Код узла, с которого начинается прохождение — должен существовать среди {@link #nodes}. */
    private String entryNode;

    private List<NodeSeedDto> nodes = new ArrayList<>();
}
