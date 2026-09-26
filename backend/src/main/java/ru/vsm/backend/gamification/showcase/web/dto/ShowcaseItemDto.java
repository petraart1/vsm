package ru.vsm.backend.gamification.showcase.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Снимок награды для витрины. {@code id} — идентификатор награды во фронтенде
 * ({@code module:<блок>}, {@code dist:<код>}, {@code streak:<дни>}, {@code shift:clean}).
 */
public record ShowcaseItemDto(
        @NotBlank @Size(max = 80) @Pattern(regexp = "[a-z]+:[A-Za-z0-9_-]+") String id,
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Pattern(regexp = "circle|hexagon|octagon|shield") String shape,
        @Size(max = 32) @Pattern(regexp = "[a-zA-Z]*") String glyph,
        @Size(max = 8) String text) {
}
