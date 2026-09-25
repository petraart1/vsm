package ru.vsm.backend.scenario.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
 * Вариант ответа в узле: дельты шкал лояльности/безопасности, целевой узел
 * ({@code null} = конец сценария сразу после этого выбора), пометки шагов
 * универсальной ролевой модели и опциональный ключ пояснения для разбора.
 *
 * <p>Важно (см. {@code dataset/standards/sto-rzd-03011-general.md}, "Применимость к нашему
 * проекту"): нельзя начислять положительную {@link #safetyDelta} за выбор, нарушающий
 * норматив (например, посадка без билета "по-дружески") — это проверяется на уровне
 * содержимого seed-данных, а не схемой БД.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "scenario_choices")
public class ScenarioChoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    /** Уникален в рамках узла (не глобально), используется в seed JSON. */
    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false)
    private String text;

    @Column(name = "loyalty_delta", nullable = false)
    @Builder.Default
    private int loyaltyDelta = 0;

    @Column(name = "safety_delta", nullable = false)
    @Builder.Default
    private int safetyDelta = 0;

    /** {@code null} = конец сценария сразу после этого выбора. */
    @Column(name = "target_node_id")
    private UUID targetNodeId;

    @Embedded
    @Builder.Default
    private RoleStepFlags roleSteps = new RoleStepFlags();

    /** Ключ пояснения для экрана разбора, не сам текст. */
    @Column(name = "explanation_key", length = 150)
    private String explanationKey;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
