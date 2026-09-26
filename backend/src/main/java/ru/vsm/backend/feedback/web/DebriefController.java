package ru.vsm.backend.feedback.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.auth.security.PlayerAccessGuard;
import ru.vsm.backend.feedback.dto.DebriefResponse;
import ru.vsm.backend.feedback.service.DebriefService;

/**
 * REST разбора прохождения сценария (экран {@code design/screens/debrief.md}).
 *
 * <p>Эндпоинт: {@code GET /api/feedback/debrief/{userProgressId}} -> {@link DebriefResponse}.
 * {@code userProgressId} — id записи {@code user_progress} из сценария.
 *
 * <p>Доступ — только владелец прохождения (JWT либо {@code X-Player-Id}) или ADMIN, иначе
 * {@code 403 forbidden} (см. {@link PlayerAccessGuard}) — раньше разбор чужого прохождения
 * читался без единой проверки владения. Владелец — не параметр запроса, а поле самого
 * прохождения ({@link DebriefService#ownerOf}), т.к. {@code userProgressId} сам по себе не
 * говорит, чьё это прохождение (тот же паттерн, что {@code NotificationController}).
 */
@RestController
@RequiredArgsConstructor
public class DebriefController {

    private final DebriefService debriefService;
    private final PlayerAccessGuard playerAccessGuard;

    @GetMapping("/api/feedback/debrief/{userProgressId}")
    public DebriefResponse getDebrief(@PathVariable UUID userProgressId, HttpServletRequest request) {
        playerAccessGuard.requireOwnerOrAdmin(request, debriefService.ownerOf(userProgressId));
        return debriefService.buildDebrief(userProgressId);
    }
}
