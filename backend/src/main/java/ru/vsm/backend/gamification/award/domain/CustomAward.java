package ru.vsm.backend.gamification.award.domain;

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

/** Награда, созданная администратором: форма медали и пиктограмма — в едином дизайн-коде наград. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gamification_custom_award")
public class CustomAward {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 300)
    private String description;

    /** circle | hexagon | octagon | shield */
    @Column(nullable = false, length = 16)
    private String shape;

    @Column(length = 32)
    private String glyph;

    /** Только для учётных записей, подтверждённых через Госуслуги. */
    @Column(name = "verified_only", nullable = false)
    private boolean verifiedOnly;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
