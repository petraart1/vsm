package ru.vsm.backend.gamification.challenge.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.auth.security.PlayerAccessGuard;
import ru.vsm.backend.gamification.challenge.service.ChallengeQueryService;
import ru.vsm.backend.gamification.challenge.web.dto.ChallengeDto;

/** GET /api/gamification/challenges?playerId=... — активные челленджи месяца с прогрессом игрока */
@RestController
@RequestMapping("/api/gamification/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeQueryService queryService;
    private final PlayerAccessGuard playerAccessGuard;

    @GetMapping
    public List<ChallengeDto> getChallenges(
            @RequestParam(required = false) UUID playerId, HttpServletRequest request) {
        if (playerId != null) {
            playerAccessGuard.requireOwnerOrAdmin(request, playerId);
        }
        return queryService.getActiveChallenges(playerId);
    }
}
