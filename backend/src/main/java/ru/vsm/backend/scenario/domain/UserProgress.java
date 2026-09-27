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

/** Прохождение сценария игроком: текущая позиция в графе и накопленные шкалы. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_progress")
public class UserProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "scenario_id", nullable = false)
    private UUID scenarioId;

    /** Узел, на котором сейчас находится игрок; null после завершения/выхода. */
    @Column(name = "current_node_id")
    private UUID currentNodeId;

    /** Дедлайн решения по {@link #currentNodeId} (now + {@code timerSeconds} узла в момент входа */
    @Column(name = "node_deadline_at")
    private Instant nodeDeadlineAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private ProgressStatus status = ProgressStatus.IN_PROGRESS;

    /**
     * Класс вагона ("портрет пассажира"), в котором игрок проходит сценарий — задаётся один раз
     * при старте прохождения ({@link #startedAt}) и не меняется до его завершения; модифицирует
     * дельту лояльности каждого применённого выбора (см. {@link CarClass}).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "car_class", nullable = false, length = 16)
    @Builder.Default
    private CarClass carClass = CarClass.STANDARD;

    @Column(name = "loyalty_score", nullable = false)
    @Builder.Default
    private int loyaltyScore = 0;

    @Column(name = "safety_score", nullable = false)
    @Builder.Default
    private int safetyScore = 0;

    /** Заполняется при завершении (status = COMPLETED). */
    @Enumerated(EnumType.STRING)
    @Column(name = "final_outcome", length = 16)
    private ScenarioOutcome finalOutcome;

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private Instant startedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    /** тренировочная попытка. Задаётся один раз при старте ({@code ScenarioPlayService.start} с */
    @Column(name = "exam_mode", nullable = false)
    @Builder.Default
    private boolean examMode = false;

    /** id {@link Exam}, если {@link #examMode}; иначе {@code null}. Без FK — см. Javadoc {@link Exam}. */
    @Column(name = "exam_id")
    private UUID examId;
}
