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
 * Запись одного сделанного выбора в рамках прохождения ({@link UserProgress}) — история выборов:
 * что выбрано на каждом узле, был ли выбор по таймауту, какие дельты фактически применились.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "scenario_choice_history")
public class ScenarioChoiceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_progress_id", nullable = false)
    private UUID userProgressId;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Column(name = "choice_id", nullable = false)
    private UUID choiceId;

    /** Порядковый номер решения в рамках прохождения (0, 1, 2, ...). */
    @Column(name = "sequence_index", nullable = false)
    private int sequenceIndex;

    /** true, если выбор применён автоматически из-за истечения таймера узла. */
    @Column(name = "was_timeout", nullable = false)
    @Builder.Default
    private boolean wasTimeout = false;

    @Column(name = "loyalty_delta_applied", nullable = false)
    private int loyaltyDeltaApplied;

    @Column(name = "safety_delta_applied", nullable = false)
    private int safetyDeltaApplied;

    @Column(name = "chosen_at", nullable = false)
    @Builder.Default
    private Instant chosenAt = Instant.now();
}
