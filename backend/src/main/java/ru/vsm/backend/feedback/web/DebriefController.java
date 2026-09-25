package ru.vsm.backend.feedback.web;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.feedback.dto.DebriefResponse;
import ru.vsm.backend.feedback.service.DebriefService;

/**
 * REST разбора прохождения сценария (экран {@code design/screens/debrief.md}).
 *
 * <p>Эндпоинт: {@code GET /api/feedback/debrief/{userProgressId}} -> {@link DebriefResponse}.
 * {@code userProgressId} — id записи {@code user_progress} из сценария.
 */
@RestController
public class DebriefController {

    private final DebriefService debriefService;

    public DebriefController(DebriefService debriefService) {
        this.debriefService = debriefService;
    }

    @GetMapping("/api/feedback/debrief/{userProgressId}")
    public DebriefResponse getDebrief(@PathVariable UUID userProgressId) {
        return debriefService.buildDebrief(userProgressId);
    }
}
