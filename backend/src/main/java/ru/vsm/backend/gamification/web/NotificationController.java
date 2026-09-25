package ru.vsm.backend.gamification.web;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.gamification.service.NotificationService;
import ru.vsm.backend.gamification.web.dto.NotificationDto;

/**
 * Уведомления игрока (новая ачивка / личный рекорд / рост в лидерборде / рекомендация).
 * Создаются автоматически при начислении очков за завершённое прохождение сценария
 * ({@code GamificationAccrualService}) — этот контроллер только читает и отмечает прочитанным.
 */
@RestController
@RequestMapping("/api/gamification/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationDto> getNotifications(
            @RequestParam UUID playerId,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return notificationService.list(playerId, unreadOnly);
    }

    @PostMapping("/{id}/read")
    public NotificationDto markRead(@PathVariable UUID id) {
        return notificationService.markRead(id);
    }

    @PostMapping("/read-all")
    public MarkAllReadResponse markAllRead(@RequestParam UUID playerId) {
        return new MarkAllReadResponse(notificationService.markAllRead(playerId));
    }

    public record MarkAllReadResponse(int markedCount) {
    }
}
