package ru.vsm.backend.gamification.award.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.auth.security.PlayerAccessGuard;
import ru.vsm.backend.gamification.award.service.CustomAwardService;
import ru.vsm.backend.gamification.award.web.dto.CustomAwardDto;

/**
 * GET /api/gamification/custom-awards?playerId=… — каталог наград администратора; с
 * {@code playerId} (только владелец или ADMIN) отмечены полученные.
 */
@RestController
@RequestMapping("/api/gamification/custom-awards")
@RequiredArgsConstructor
public class CustomAwardController {

    private final CustomAwardService service;
    private final PlayerAccessGuard playerAccessGuard;

    @GetMapping
    public List<CustomAwardDto> catalog(@RequestParam(required = false) UUID playerId, HttpServletRequest request) {
        if (playerId != null) {
            playerAccessGuard.requireOwnerOrAdmin(request, playerId);
        }
        return service.catalogFor(playerId);
    }
}
