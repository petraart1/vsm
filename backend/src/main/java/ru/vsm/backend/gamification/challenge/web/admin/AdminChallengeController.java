package ru.vsm.backend.gamification.challenge.web.admin;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.vsm.backend.gamification.challenge.domain.Challenge;
import ru.vsm.backend.gamification.challenge.domain.ChallengeGoalType;
import ru.vsm.backend.gamification.challenge.repository.ChallengeRepository;
import ru.vsm.backend.gamification.domain.AchievementCode;
import ru.vsm.backend.gamification.domain.NotificationType;
import ru.vsm.backend.gamification.service.NotificationService;

/**
 * Админ-панель: события (челленджи) — список, создание и досрочное завершение. Путь под
 * {@code /api/admin/**}, поэтому доступен только роли ADMIN (см. {@code SecurityConfig}).
 * Удаления нет намеренно: по событию уже может быть прогресс игроков, поэтому событие
 * завершается (конец периода = сейчас) и остаётся в истории.
 *
 * <p>Создание события рассылает всем уже известным профилям игрока уведомление
 * {@code NEW_CHALLENGE} ({@link NotificationService#notifyAllPlayers}) — в отличие от личного
 * {@code CHALLENGE_COMPLETED} при выполнении, это уведомление о самом факте появления цели.
 * Сидер стартовых челленджей месяца ({@code ChallengeSeeder}) этот путь не использует и
 * уведомлений не рассылает.
 */
@RestController
@RequestMapping("/api/admin/challenges")
@RequiredArgsConstructor
public class AdminChallengeController {

    private final ChallengeRepository challengeRepository;
    private final NotificationService notificationService;

    @GetMapping
    @Transactional(readOnly = true)
    public List<AdminChallengeDto> list() {
        Instant now = Instant.now();
        return challengeRepository.findAll().stream()
                .sorted(Comparator.comparing(Challenge::getStartsAt).reversed())
                .map(c -> AdminChallengeDto.from(c, now))
                .toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<AdminChallengeDto> create(@Valid @RequestBody AdminChallengeRequest request) {
        ChallengeGoalType goalType = ChallengeGoalType.valueOf(request.goalType());
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Конец события должен быть позже начала");
        }
        if (goalType == ChallengeGoalType.BLOCK_SCENARIOS_NO_FAILURE
                && (request.targetBlock() == null || request.targetBlock().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для этой цели нужен блок ситуаций");
        }
        if (goalType == ChallengeGoalType.SAFETY_STREAK && request.safetyThreshold() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для серии по безопасности нужен порог шкалы");
        }
        String achievement = blankToNull(request.rewardAchievementCode());
        if (achievement != null && Arrays.stream(AchievementCode.values()).noneMatch(a -> a.name().equals(achievement))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестная ачивка: " + achievement);
        }
        Challenge challenge = Challenge.builder()
                .code("event-" + UUID.randomUUID().toString().substring(0, 8))
                .title(request.title().trim())
                .description(request.description().trim())
                .goalType(goalType)
                .targetBlock(blankToNull(request.targetBlock()))
                .targetCount(request.targetCount())
                .safetyThreshold(goalType == ChallengeGoalType.SAFETY_STREAK ? request.safetyThreshold() : null)
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .rewardPoints(request.rewardPoints())
                .rewardAchievementCode(achievement)
                .build();
        Challenge saved = challengeRepository.save(challenge);
        notificationService.notifyAllPlayers(NotificationType.NEW_CHALLENGE,
                "Новое событие: " + saved.getTitle(), saved.getDescription());
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminChallengeDto.from(saved, Instant.now()));
    }

    /** Досрочно завершить событие: конец периода переносится на текущий момент. */
    @PostMapping("/{id}/finish")
    @Transactional
    public AdminChallengeDto finish(@PathVariable UUID id) {
        Challenge challenge = challengeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Событие не найдено"));
        Instant now = Instant.now();
        if (challenge.getEndsAt().isAfter(now)) {
            challenge.setEndsAt(now.isBefore(challenge.getStartsAt()) ? challenge.getStartsAt() : now);
        }
        return AdminChallengeDto.from(challengeRepository.save(challenge), now);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
