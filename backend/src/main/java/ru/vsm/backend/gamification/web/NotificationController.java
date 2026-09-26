package ru.vsm.backend.gamification.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.auth.security.PlayerAccessGuard;
import ru.vsm.backend.gamification.service.NotificationService;
import ru.vsm.backend.gamification.web.dto.NotificationDto;

/**
 * Уведомления игрока (новая ачивка / личный рекорд / рост в лидерборде / рекомендация).
 * Создаются автоматически при начислении очков за завершённое прохождение сценария
 * ({@code GamificationAccrualService}) — этот контроллер только читает и отмечает прочитанным.
 *
 * <p>Доступ на всех трёх операциях — только владелец (JWT либо {@code X-Player-Id}) или ADMIN,
 * иначе {@code 403 forbidden} (см. {@link PlayerAccessGuard}). Для {@code POST /{id}/read}
 * владелец — не параметр запроса, а поле самого уведомления ({@link NotificationService#ownerOf}),
 * т.к. id уведомления сам по себе не говорит, чьё оно.
 */
@RestController
@RequestMapping("/api/gamification/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final PlayerAccessGuard playerAccessGuard;

    @GetMapping
    public List<NotificationDto> getNotifications(
            @RequestParam UUID playerId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            HttpServletRequest request) {
        playerAccessGuard.requireOwnerOrAdmin(request, playerId);
        return notificationService.list(playerId, unreadOnly);
    }

    @PostMapping("/{id}/read")
    public NotificationDto markRead(@PathVariable UUID id, HttpServletRequest request) {
        playerAccessGuard.requireOwnerOrAdmin(request, notificationService.ownerOf(id));
        return notificationService.markRead(id);
    }

    @PostMapping("/read-all")
    public MarkAllReadResponse markAllRead(@RequestParam UUID playerId, HttpServletRequest request) {
        playerAccessGuard.requireOwnerOrAdmin(request, playerId);
        return new MarkAllReadResponse(notificationService.markAllRead(playerId));
    }

    public record MarkAllReadResponse(int markedCount) {
    }
}
