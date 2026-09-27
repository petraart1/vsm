package ru.vsm.backend.gamification.award.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Тело {@code POST /api/admin/awards}. */
public record CustomAwardRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 300) String description,
        @NotBlank @Pattern(regexp = "circle|hexagon|octagon|shield") String shape,
        @Size(max = 32) @Pattern(regexp = "[a-zA-Z]*") String glyph,
        boolean verifiedOnly) {
}
