package ru.vsm.backend.gamification.challenge.service;

import java.time.Instant;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vsm.backend.gamification.challenge.domain.Challenge;
import ru.vsm.backend.gamification.challenge.domain.ChallengeGoalType;
import ru.vsm.backend.gamification.challenge.repository.ChallengeRepository;
import ru.vsm.backend.gamification.domain.AchievementCode;

/**
 * Заполняет каталог челленджей на текущий календарный месяц при каждом старте приложения.
 * Идемпотентно: код челленджа включает год-месяц ({@code "<ключ шаблона>-<YYYY-MM>"}), поэтому
 * повторный старт в том же месяце ничего не создаёт заново ({@link ChallengeRepository#findByCode}
 * по каждому шаблону), а переход на новый месяц заводит новый набор строк без релиза — даты
 * периода не захардкожены в миграции.
 *
 * <p>Награда каждого шаблона — очки + ачивка {@link AchievementCode#CHALLENGE_CHAMPION} (одна
 * общая ачивка "за выполнение любого челленджа месяца" — выдаётся один раз, повторное выполнение
 * другого челленджа в том же или следующем месяце очков ачивки не добавляет, но продолжает
 * начислять {@code rewardPoints}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(100)
public class ChallengeSeeder implements ApplicationRunner {

    private final ChallengeRepository challengeRepository;

    private record Template(
            String key,
            String title,
            String description,
            ChallengeGoalType goalType,
            String targetBlock,
            int targetCount,
            Integer safetyThreshold,
            int rewardPoints) {
    }

    private static final List<Template> TEMPLATES = List.of(
            new Template("boarding-no-failure", "Мастер посадки",
                    "Завершите 5 сценариев блока «посадка и билеты» без исхода «провал»",
                    ChallengeGoalType.BLOCK_SCENARIOS_NO_FAILURE, "boarding", 5, null, 150),
            new Template("medical-no-failure", "Медицинская готовность",
                    "Завершите 3 сценария блока «медицинская помощь» без исхода «провал»",
                    ChallengeGoalType.BLOCK_SCENARIOS_NO_FAILURE, "medical", 3, null, 170),
            new Template("safety-streak", "Безопасная серия",
                    "Пройдите 3 сценария подряд с итоговым рейтингом безопасности не ниже 20",
                    ChallengeGoalType.SAFETY_STREAK, null, 3, 20, 200),
            new Template("role-model", "Полный протокол",
                    "Пройдите 5 сценариев, ни разу не пропустив ни один из 4 шагов ролевой модели ответа",
                    ChallengeGoalType.ROLE_MODEL_ALL_STEPS, null, 5, null, 180));

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        YearMonth month = YearMonth.now();
        Instant startsAt = month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endsAt = month.atEndOfMonth().atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);

        for (Template t : TEMPLATES) {
            String code = t.key() + "-" + month;
            if (challengeRepository.findByCode(code).isPresent()) {
                continue;
            }
            challengeRepository.save(Challenge.builder()
                    .code(code)
                    .title(t.title())
                    .description(t.description())
                    .goalType(t.goalType())
                    .targetBlock(t.targetBlock())
                    .targetCount(t.targetCount())
                    .safetyThreshold(t.safetyThreshold())
                    .startsAt(startsAt)
                    .endsAt(endsAt)
                    .rewardPoints(t.rewardPoints())
                    .rewardAchievementCode(AchievementCode.CHALLENGE_CHAMPION.name())
                    .build());
            log.info("Засеян челлендж месяца: {}", code);
        }
    }
}
