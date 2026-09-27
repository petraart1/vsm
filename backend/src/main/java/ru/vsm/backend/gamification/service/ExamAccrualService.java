package ru.vsm.backend.gamification.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.vsm.backend.gamification.domain.AchievementCode;
import ru.vsm.backend.gamification.domain.ExamAccrualLogEntry;
import ru.vsm.backend.gamification.domain.NotificationType;
import ru.vsm.backend.gamification.domain.PlayerAchievement;
import ru.vsm.backend.gamification.domain.PlayerProfile;
import ru.vsm.backend.gamification.repository.ExamAccrualLogRepository;
import ru.vsm.backend.gamification.repository.PlayerAchievementRepository;
import ru.vsm.backend.gamification.repository.PlayerProfileRepository;
import ru.vsm.backend.scenario.domain.ExamGrade;
import ru.vsm.backend.scenario.event.ExamCompletedEvent;

/** Бонусное начисление очков за завершение экзамена целиком, по {@link ExamCompletedEvent} (см. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamAccrualService {

    /** Бонус за оценку "отлично" ({@link ExamGrade#EXCELLENT}) — максимальная итоговая оценка. */
    private static final int BONUS_EXCELLENT = 300;
    /** Бонус за оценку "хорошо" ({@link ExamGrade#GOOD}). */
    private static final int BONUS_GOOD = 150;
    /** Бонус за оценку "удовлетворительно" ({@link ExamGrade#SATISFACTORY}). */
    private static final int BONUS_SATISFACTORY = 50;
    /** Оценка "неудовлетворительно" ({@link ExamGrade#UNSATISFACTORY}) бонуса не даёт. */
    private static final int BONUS_UNSATISFACTORY = 0;

    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerAchievementRepository playerAchievementRepository;
    private final ExamAccrualLogRepository examAccrualLogRepository;
    private final NotificationService notificationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processExam(ExamCompletedEvent event) {
        if (examAccrualLogRepository.existsByExamId(event.examId())) {
            log.info("Скип начисления бонуса за экзамен: examId={} уже обработан", event.examId());
            return;
        }

        int bonusPoints = bonusPoints(event.grade());

        Optional<PlayerProfile> existingProfile = playerProfileRepository.findById(event.playerId());
        PlayerProfile profile = existingProfile
                .orElseGet(() -> PlayerProfile.builder().id(event.playerId()).build());
        int scoreBeforeBonus = profile.getTotalScore();
        profile.setTotalScore(scoreBeforeBonus + bonusPoints);
        profile.setUpdatedAt(Instant.now());
        playerProfileRepository.save(profile);
        notificationService.notifyLevelUpIfChanged(event.playerId(), scoreBeforeBonus, profile.getTotalScore(), null);

        examAccrualLogRepository.save(ExamAccrualLogEntry.builder()
                .examId(event.examId())
                .playerId(event.playerId())
                .grade(event.grade())
                .bonusPointsAwarded(bonusPoints)
                .build());

        notificationService.create(event.playerId(), NotificationType.EXAM_COMPLETED,
                "Экзамен завершён: " + gradeTitle(event.grade()),
                "Бонус за экзамен: %d очков".formatted(bonusPoints),
                null);

        if (event.grade() == ExamGrade.EXCELLENT
                && !playerAchievementRepository.existsByPlayerIdAndAchievementCode(
                        event.playerId(), AchievementCode.CERTIFICATE)) {
            playerAchievementRepository.save(PlayerAchievement.builder()
                    .playerId(event.playerId())
                    .achievementCode(AchievementCode.CERTIFICATE)
                    .build());
            notificationService.create(event.playerId(), NotificationType.ACHIEVEMENT_UNLOCKED,
                    "Новая ачивка: " + AchievementCode.CERTIFICATE.title(),
                    AchievementCode.CERTIFICATE.description(), null);
        }

        log.info("Экзамен {} игрока {} завершён с оценкой {}: начислено {} бонусных очков",
                event.examId(), event.playerId(), event.grade(), bonusPoints);
    }

    /** Таблица бонусов по итоговой оценке экзамена — см. Javadoc класса. */
    private int bonusPoints(ExamGrade grade) {
        return switch (grade) {
            case EXCELLENT -> BONUS_EXCELLENT;
            case GOOD -> BONUS_GOOD;
            case SATISFACTORY -> BONUS_SATISFACTORY;
            case UNSATISFACTORY -> BONUS_UNSATISFACTORY;
        };
    }

    private String gradeTitle(ExamGrade grade) {
        return switch (grade) {
            case EXCELLENT -> "отлично";
            case GOOD -> "хорошо";
            case SATISFACTORY -> "удовлетворительно";
            case UNSATISFACTORY -> "неудовлетворительно";
        };
    }
}
