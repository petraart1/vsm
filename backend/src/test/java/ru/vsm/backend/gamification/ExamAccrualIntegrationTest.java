package ru.vsm.backend.gamification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.gamification.domain.AchievementCode;
import ru.vsm.backend.gamification.domain.NotificationType;
import ru.vsm.backend.gamification.domain.PlayerProfile;
import ru.vsm.backend.gamification.repository.PlayerAchievementRepository;
import ru.vsm.backend.gamification.repository.PlayerProfileRepository;
import ru.vsm.backend.gamification.service.NotificationService;
import ru.vsm.backend.gamification.web.dto.NotificationDto;
import ru.vsm.backend.scenario.domain.ExamGrade;
import ru.vsm.backend.scenario.event.ExamCompletedEvent;

/**
 * Бонус очков и ачивка "Сертификат" за завершение экзамена целиком ({@code ExamAccrualService},
 * {@code gamification.event.ExamCompletedEventListener}) — таблица бонусов по итоговой оценке,
 * ачивка только за "отлично", идемпотентность повторной доставки события по {@code examId} (см.
 * {@code ScenarioPlayApiIntegrationTest}/{@code ExamApiIntegrationTest} для сквозного REST-пути,
 * который публикует это событие через {@code ExamService.finishExam}).
 */
@SpringBootTest
@Testcontainers
class ExamAccrualIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

    @Autowired
    private PlayerAchievementRepository playerAchievementRepository;

    @Autowired
    private NotificationService notificationService;

    private static ExamCompletedEvent examEvent(UUID playerId, ExamGrade grade) {
        Instant now = Instant.now();
        return new ExamCompletedEvent(
                UUID.randomUUID(), playerId, 80.0, 90.0, 0.8, grade, List.of(),
                now.minusSeconds(600), now);
    }

    @Test
    void excellentGradeAwardsBonusAndCertificateAchievementAndNotification() {
        UUID playerId = UUID.randomUUID();

        eventPublisher.publishEvent(examEvent(playerId, ExamGrade.EXCELLENT));

        PlayerProfile profile = playerProfileRepository.findById(playerId).orElseThrow();
        assertThat(profile.getTotalScore()).isEqualTo(300);
        assertThat(playerAchievementRepository.existsByPlayerIdAndAchievementCode(
                playerId, AchievementCode.CERTIFICATE)).isTrue();

        List<NotificationDto> notifications = notificationService.list(playerId, false);
        assertThat(notifications).extracting(NotificationDto::type)
                .contains(NotificationType.EXAM_COMPLETED.name(), NotificationType.ACHIEVEMENT_UNLOCKED.name());
    }

    @Test
    void nonExcellentGradesAwardBonusByTableWithoutCertificate() {
        UUID goodPlayer = UUID.randomUUID();
        eventPublisher.publishEvent(examEvent(goodPlayer, ExamGrade.GOOD));
        assertThat(playerProfileRepository.findById(goodPlayer).orElseThrow().getTotalScore()).isEqualTo(150);
        assertThat(playerAchievementRepository.existsByPlayerIdAndAchievementCode(
                goodPlayer, AchievementCode.CERTIFICATE)).isFalse();

        UUID satisfactoryPlayer = UUID.randomUUID();
        eventPublisher.publishEvent(examEvent(satisfactoryPlayer, ExamGrade.SATISFACTORY));
        assertThat(playerProfileRepository.findById(satisfactoryPlayer).orElseThrow().getTotalScore())
                .isEqualTo(50);

        UUID unsatisfactoryPlayer = UUID.randomUUID();
        eventPublisher.publishEvent(examEvent(unsatisfactoryPlayer, ExamGrade.UNSATISFACTORY));
        assertThat(playerProfileRepository.findById(unsatisfactoryPlayer).orElseThrow().getTotalScore())
                .isZero();
    }

    @Test
    void replayingSameExamCompletedEventDoesNotDuplicateBonusOrAchievement() {
        UUID playerId = UUID.randomUUID();
        ExamCompletedEvent event = examEvent(playerId, ExamGrade.EXCELLENT);

        eventPublisher.publishEvent(event);
        eventPublisher.publishEvent(event);

        assertThat(playerProfileRepository.findById(playerId).orElseThrow().getTotalScore()).isEqualTo(300);
        assertThat(playerAchievementRepository.findByPlayerId(playerId)).hasSize(1);
    }
}
