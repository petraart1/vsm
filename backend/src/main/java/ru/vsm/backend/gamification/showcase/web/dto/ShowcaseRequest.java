package ru.vsm.backend.gamification.showcase.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** PUT /api/gamification/showcase — полная замена витрины текущего игрока (не более 6 наград). */
public record ShowcaseRequest(
        @Pattern(regexp = "metal|enamel|glass") String finish,
        @NotNull @Size(max = 6) List<@Valid @NotNull ShowcaseItemDto> items) {
}
