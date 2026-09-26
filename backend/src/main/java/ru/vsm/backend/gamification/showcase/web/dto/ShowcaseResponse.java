package ru.vsm.backend.gamification.showcase.web.dto;

import java.util.List;

/** Витрина игрока для показа коллегам: отделка медалей и награды в порядке, выбранном владельцем. */
public record ShowcaseResponse(String finish, List<ShowcaseItemDto> items) {
}
