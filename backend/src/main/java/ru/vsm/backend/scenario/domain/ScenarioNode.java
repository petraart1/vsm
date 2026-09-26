package ru.vsm.backend.scenario.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Узел графа сценария: реплика/ситуация, опциональный таймер решения (сек) с выбором
 * по умолчанию при истечении, опциональный терминальный исход.
 *
 * <p>См. пояснение про отсутствие JPA-ассоциаций в {@link Scenario}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "scenario_nodes")
public class ScenarioNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "scenario_id", nullable = false)
    private UUID scenarioId;

    /** Уникален в рамках сценария (не глобально), используется в seed JSON. */
    @Column(nullable = false, length = 100)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 32)
    private NodeType nodeType;

    /** Текст реплики пассажира / описание ситуации, которую видит игрок. */
    @Column(nullable = false)
    private String text;

    /** NULL = решение не ограничено по времени. */
    @Column(name = "timer_seconds")
    private Integer timerSeconds;

    /** Выбор, который засчитывается автоматически при истечении {@link #timerSeconds}. */
    @Column(name = "default_choice_id")
    private UUID defaultChoiceId;

    @Column(name = "is_terminal", nullable = false)
    @Builder.Default
    private boolean terminal = false;

    /**
     * Узел закадровой коммуникации: решение здесь не долетает до пассажира (служебная рация,
     * внутренние переговоры бригады) — эффект только на шкалы, реакция пассажира не показывается.
     * Используется разбором прохождения ({@code feedback.service.DebriefService}) как основной
     * признак скрытой механики; эвристика по одинаковой дельте лояльности у всех альтернатив узла
     * остаётся резервным вариантом для узлов, где флаг не проставлен.
     */
    @Column(name = "hidden_from_passenger", nullable = false)
    @Builder.Default
    private boolean hiddenFromPassenger = false;

    /** Заполняется только если {@link #terminal} = true. */
    @Enumerated(EnumType.STRING)
    @Column(name = "terminal_outcome", length = 16)
    private ScenarioOutcome terminalOutcome;

    /** Краткое резюме исхода для экрана разбора (только терминальные узлы). */
    @Column(name = "outcome_summary")
    private String outcomeSummary;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
