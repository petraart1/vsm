package ru.vsm.backend.scenario.web;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.scenario.service.ScenarioPlayService;
import ru.vsm.backend.scenario.web.dto.ChoiceAppliedResponse;
import ru.vsm.backend.scenario.web.dto.ProgressStateResponse;

/**
 * Прохождение сценария: старт, текущий узел, выбор варианта, явный таймаут.
 *
 * <p>Идентификация игрока — простая, без Spring Security: заголовок {@value #PLAYER_ID_HEADER}
 * с UUID игрока на каждый запрос. Это тот же UUID, что домен gamification использует как {@code playerId}
 * (общее пространство идентификаторов между доменами, без FK в БД — см. Javadoc {@code UserProgress.userId}).
 */
@RestController
@RequestMapping("/api/scenarios")
@RequiredArgsConstructor
public class ScenarioPlayController {

    public static final String PLAYER_ID_HEADER = "X-Player-Id";

    private final ScenarioPlayService scenarioPlayService;

    @PostMapping("/{scenarioId}/progress")
    @ResponseStatus(HttpStatus.CREATED)
    public ProgressStateResponse start(
            @PathVariable UUID scenarioId, @RequestHeader(PLAYER_ID_HEADER) String playerIdHeader) {
        return scenarioPlayService.start(scenarioId, parsePlayerId(playerIdHeader));
    }

    @GetMapping("/progress/{progressId}")
    public ProgressStateResponse getProgress(
            @PathVariable UUID progressId, @RequestHeader(PLAYER_ID_HEADER) String playerIdHeader) {
        return scenarioPlayService.getProgress(progressId, parsePlayerId(playerIdHeader));
    }

    @PostMapping("/progress/{progressId}/choices/{choiceId}")
    public ChoiceAppliedResponse choose(
            @PathVariable UUID progressId, @PathVariable UUID choiceId,
            @RequestHeader(PLAYER_ID_HEADER) String playerIdHeader) {
        return scenarioPlayService.choose(progressId, parsePlayerId(playerIdHeader), choiceId);
    }

    /** Явный запрос клиента "время вышло" (например, если WebSocket-пуш недоступен). */
    @PostMapping("/progress/{progressId}/timeout")
    public ChoiceAppliedResponse timeout(
            @PathVariable UUID progressId, @RequestHeader(PLAYER_ID_HEADER) String playerIdHeader) {
        return scenarioPlayService.timeout(progressId, parsePlayerId(playerIdHeader));
    }

    private UUID parsePlayerId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Заголовок " + PLAYER_ID_HEADER + " должен быть UUID, получено: '" + raw + "'", e);
        }
    }
}
