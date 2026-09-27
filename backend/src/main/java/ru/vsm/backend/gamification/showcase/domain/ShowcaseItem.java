package ru.vsm.backend.gamification.showcase.domain;

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

/** Одна награда на витрине игрока — самодостаточный снимок (название, форма медали, пиктограмма */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gamification_showcase_item")
public class ShowcaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "public_id", nullable = false, length = 32)
    private String publicId;

    @Column(name = "sort_order", nullable = false)
    private int position;

    @Column(name = "award_id", nullable = false, length = 80)
    private String awardId;

    @Column(nullable = false, length = 160)
    private String title;

    /** Форма медали: circle | hexagon | octagon | shield. */
    @Column(nullable = false, length = 16)
    private String shape;

    /** Имя пиктограммы фронтенда (Icon) либо null, если медаль несёт надпись {@link #label}. */
    @Column(length = 32)
    private String glyph;

    /** Короткая надпись на медали (например, число дней серии). */
    @Column(length = 8)
    private String label;

    /** Отделка витрины владельца: metal | enamel | glass. */
    @Column(nullable = false, length = 16)
    private String finish;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
