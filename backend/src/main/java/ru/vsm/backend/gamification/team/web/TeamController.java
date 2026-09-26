package ru.vsm.backend.gamification.team.web;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.vsm.backend.gamification.team.service.TeamService;
import ru.vsm.backend.gamification.team.web.dto.TeamDto;

/**
 * Каталог команд (бригад/депо) и вступление в команду. Идентификация игрока — тот же заголовок
 * {@value #PLAYER_ID_HEADER}, что у остальных пишущих игровых эндпоинтов (см.
 * {@code ScenarioPlayController}); при валидном {@code Authorization: Bearer} значение
 * подменяется playerId из токена ещё до контроллера ({@code JwtAuthenticationFilter}), так что
 * этот код одинаково работает и для анонимного заголовка, и для авторизованного игрока.
 */
@RestController
@RequestMapping("/api/gamification/teams")
@RequiredArgsConstructor
public class TeamController {

    public static final String PLAYER_ID_HEADER = "X-Player-Id";

    private final TeamService teamService;

    @GetMapping
    public List<TeamDto> listTeams() {
        return teamService.listTeams();
    }

    /** Вступление в команду; повторный вызов с другим {@code id} — смена команды (разрешена). */
    @PostMapping("/{id}/join")
    public TeamDto join(@PathVariable UUID id, @RequestHeader(PLAYER_ID_HEADER) String playerIdHeader) {
        return teamService.join(id, parsePlayerId(playerIdHeader));
    }

    private UUID parsePlayerId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Заголовок " + PLAYER_ID_HEADER + " должен быть UUID, получено: '" + raw + "'", e);
        }
    }
}
