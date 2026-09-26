package ru.vsm.backend.gamification.team.web;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.gamification.team.service.TeamService;
import ru.vsm.backend.gamification.team.web.dto.TeamLeaderboardEntryDto;

/**
 * GET /api/gamification/leaderboard/teams — рейтинг команд по среднему баллу участника (не по
 * сумме — обоснование см. javadoc {@link TeamService}).
 */
@RestController
@RequestMapping("/api/gamification/leaderboard/teams")
@RequiredArgsConstructor
public class TeamLeaderboardController {

    private final TeamService teamService;

    @GetMapping
    public List<TeamLeaderboardEntryDto> getTeamLeaderboard() {
        return teamService.getLeaderboard();
    }
}
