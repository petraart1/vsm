package ru.vsm.backend.scenario.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Сценарий — шапка графа узлов ({@link ScenarioNode}). Соответствует одной ситуации
 * из {@code dataset/scenarios/situations-index.json} (см. {@link #situationRefId}).
 *
 * <p>Внешние ключи между scenario/node/choice хранятся как обычные UUID-колонки, а не как
 * JPA-ассоциации ({@code @ManyToOne}/{@code @OneToMany}): граф может содержать циклы и
 * "вперёд смотрящие" ссылки (выбор в узле N ссылается на узел N+3 или на себя), а сидер
 * заполняет их в несколько проходов (см. {@code seed/ScenarioSeedLoader}). Такой подход проще
 * и предсказуемее, чем управлять Hibernate-графом с отложенными ссылками и каскадами.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "scenarios")
public class Scenario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Человекочитаемый уникальный код, используется в seed JSON и как естественный ключ. */
    @Column(nullable = false, unique = true, length = 100)
    private String code;

    /** id ситуации из situations-index.json, для трассируемости к датасету. */
    @Column(name = "situation_ref_id")
    private Integer situationRefId;

    /** Ключ блока датасета: boarding/baggage/safety/seating/comfort/catering/medical/lost_found/conflict/misc. */
    @Column(nullable = false, length = 32)
    private String block;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "is_flagship", nullable = false)
    @Builder.Default
    private boolean flagship = false;

    /** Узел, с которого начинается прохождение. */
    @Column(name = "entry_node_id")
    private UUID entryNodeId;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
