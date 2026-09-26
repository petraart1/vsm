package ru.vsm.backend.scenario.web;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.scenario.domain.CarClass;
import ru.vsm.backend.scenario.service.ExamService;
import ru.vsm.backend.scenario.web.dto.ExamResponse;
import ru.vsm.backend.scenario.web.dto.ProgressStateResponse;

/**
 * Режим экзамена: набор сценариев, пройденных подряд без подсказок (см. Javadoc {@link ExamService}).
 * Идентификация игрока — тот же заголовок {@value ScenarioPlayController#PLAYER_ID_HEADER}, что и у
 * обычного прохождения (подменяется проверенным JWT, если он есть — см. {@code JwtAuthenticationFilter}).
 */
@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    /**
     * {@code size} — сколько сценариев в экзамене (по умолчанию 10); {@code carClass} — единый
     * "портрет пассажира" для всех сценариев этого экзамена (по умолчанию {@code STANDARD}).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExamResponse create(
            @RequestHeader(ScenarioPlayController.PLAYER_ID_HEADER) String playerIdHeader,
            @RequestParam(required = false, defaultValue = "STANDARD") CarClass carClass,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        return examService.createExam(parsePlayerId(playerIdHeader), carClass, size);
    }

    @GetMapping("/{examId}")
    public ExamResponse get(
            @PathVariable UUID examId, @RequestHeader(ScenarioPlayController.PLAYER_ID_HEADER) String playerIdHeader) {
        return examService.getExam(examId, parsePlayerId(playerIdHeader));
    }

    /**
     * Начинает (или возвращает уже начатое) прохождение текущего непройденного пункта экзамена.
     * Дальше игрок ходит по нему обычными эндпоинтами {@link ScenarioPlayController}
     * ({@code choices}/{@code timeout}) — этот метод только создаёт прохождение с {@code examMode}.
     */
    @PostMapping("/{examId}/current")
    public ProgressStateResponse startCurrent(
            @PathVariable UUID examId, @RequestHeader(ScenarioPlayController.PLAYER_ID_HEADER) String playerIdHeader) {
        return examService.startCurrentScenario(examId, parsePlayerId(playerIdHeader));
    }

    private UUID parsePlayerId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Заголовок " + ScenarioPlayController.PLAYER_ID_HEADER + " должен быть UUID, получено: '" + raw + "'", e);
        }
    }
}
