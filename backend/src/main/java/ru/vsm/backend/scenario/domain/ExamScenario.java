package ru.vsm.backend.scenario.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Один пункт экзамена ({@link Exam}) — сценарий, зафиксированный на позиции {@link #sortOrder} при
 * создании экзамена ({@code ExamService.createExam}). {@link #block}/{@link #flagship} —
 * денормализованная копия соответствующих полей {@link Scenario} на момент создания экзамена (для
 * подсчёта "слабых блоков" в итоге без join'а и без риска, что после сдачи экзамена сценарий
 * поменяет блок/флаг флагмана в редакторе).
 *
 * <p>{@link #userProgressId} — {@code null}, пока игрок не начал именно этот пункт
 * ({@code ExamService.startCurrentScenario} создаёт {@link UserProgress} с
 * {@code examMode=true}/{@code examId} и записывает сюда его id). {@link #completed}/{@link #outcome}/
 * {@link #loyaltyScore}/{@link #safetyScore} заполняются, когда это прохождение завершается
 * (см. {@code ExamService.recordScenarioCompleted}, слушает {@code ScenarioCompletedEvent}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "exam_scenario")
public class ExamScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "scenario_id", nullable = false)
    private UUID scenarioId;

    @Column(nullable = false, length = 32)
    private String block;

    @Column(nullable = false)
    @Builder.Default
    private boolean flagship = false;

    @Column(name = "user_progress_id")
    private UUID userProgressId;

    @Column(nullable = false)
    @Builder.Default
    private boolean completed = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ScenarioOutcome outcome;

    @Column(name = "loyalty_score")
    private Integer loyaltyScore;

    @Column(name = "safety_score")
    private Integer safetyScore;
}
