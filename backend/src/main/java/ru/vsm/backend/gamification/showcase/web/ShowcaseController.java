package ru.vsm.backend.gamification.showcase.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.vsm.backend.auth.security.PlayerAccessGuard;
import ru.vsm.backend.gamification.showcase.service.ShowcaseService;
import ru.vsm.backend.gamification.showcase.web.dto.ShowcaseRequest;
import ru.vsm.backend.gamification.showcase.web.dto.ShowcaseResponse;

/**
 * Витрина наград.
 * <ul>
 *   <li>{@code PUT /api/gamification/showcase} — заменить свою витрину (личность — JWT или
 *   {@code X-Player-Id}, как у остальных игровых эндпоинтов);</li>
 *   <li>{@code GET /api/gamification/showcase/{publicId}} — публичная витрина коллеги по
 *   {@code publicId} из лидерборда. Пустая витрина — 200 с пустым списком.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/gamification/showcase")
@RequiredArgsConstructor
public class ShowcaseController {

    private final ShowcaseService showcaseService;
    private final PlayerAccessGuard playerAccessGuard;

    @PutMapping
    public ShowcaseResponse replace(@Valid @RequestBody ShowcaseRequest body, HttpServletRequest request) {
        UUID playerId = playerAccessGuard.currentPlayerId(request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Нужна личность игрока"));
        return showcaseService.replace(playerId, body);
    }

    @GetMapping("/{publicId}")
    public ShowcaseResponse byPublicId(@PathVariable String publicId) {
        if (!publicId.matches("[0-9a-f]{16}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "publicId — 16 hex-символов");
        }
        return showcaseService.byPublicId(publicId);
    }
}
