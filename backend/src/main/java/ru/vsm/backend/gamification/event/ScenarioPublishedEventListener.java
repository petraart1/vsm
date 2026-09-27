package ru.vsm.backend.gamification.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.vsm.backend.gamification.domain.NotificationType;
import ru.vsm.backend.gamification.service.NotificationService;
import ru.vsm.backend.scenario.event.ScenarioPublishedEvent;

/** Слушатель {@link ScenarioPublishedEvent} на стороне gamification: рассылает уведомление */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScenarioPublishedEventListener {

    private final NotificationService notificationService;

    @EventListener
    public void onScenarioPublished(ScenarioPublishedEvent event) {
        try {
            int notified = notificationService.notifyAllPlayers(NotificationType.NEW_SCENARIO,
                    "Новый сценарий: " + event.title(),
                    "Доступна новая ситуация в блоке «%s» — попробуйте её в тренажёре"
                            .formatted(event.block()));
            log.info("Уведомление NEW_SCENARIO о сценарии {} разослано {} игрокам",
                    event.scenarioCode(), notified);
        } catch (Exception ex) {
            log.error("Не удалось разослать уведомления о новом сценарии {}", event.scenarioCode(), ex);
        }
    }
}
