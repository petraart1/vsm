package ru.vsm.backend.gamification.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vsm.backend.gamification.domain.AchievementCode;
import ru.vsm.backend.gamification.domain.CompetencyScore;
import ru.vsm.backend.gamification.domain.PlayerAchievement;
import ru.vsm.backend.gamification.domain.PlayerLevel;
import ru.vsm.backend.gamification.domain.PlayerProfile;
import ru.vsm.backend.auth.security.PlayerPublicIdService;
import ru.vsm.backend.gamification.repository.CompetencyScoreRepository;
import ru.vsm.backend.gamification.repository.PlayerAchievementRepository;
import ru.vsm.backend.gamification.repository.PlayerProfileRepository;
import ru.vsm.backend.gamification.web.dto.AchievementDto;
import ru.vsm.backend.gamification.web.dto.BlockProgressDto;
import ru.vsm.backend.gamification.web.dto.LeaderboardEntryDto;
import ru.vsm.backend.gamification.web.dto.LeaderboardResponse;
import ru.vsm.backend.gamification.web.dto.ProfileResponse;

/**
 * Чтение профиля/лидерборда/каталога ачивок для REST-контроллеров. Только чтение — начисление
 * очков делает {@link GamificationAccrualService} по событию.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GamificationQueryService {

    /** 51 ситуация из dataset/scenarios/situations-index.json — общий знаменатель прогресса. */
    private static final int TOTAL_SCENARIOS_AVAILABLE = 51;

    private final PlayerProfileRepository playerProfileRepository;
    private final CompetencyScoreRepository competencyScoreRepository;
    private final PlayerAchievementRepository playerAchievementRepository;
    private final PlayerPublicIdService playerPublicIdService;

    public ProfileResponse getProfile(UUID playerId) {
        Optional<PlayerProfile> profileOpt = playerProfileRepository.findById(playerId);

        int totalScore = profileOpt.map(PlayerProfile::getTotalScore).orElse(0);
        int scenariosCompleted = profileOpt.map(PlayerProfile::getScenariosCompleted).orElse(0);
        String displayName = profileOpt.map(PlayerProfile::getDisplayName)
                .filter(name -> name != null && !name.isBlank())
                .orElseGet(() -> generatedDisplayName(playerId));

        List<BlockProgressDto> blockProgress = competencyScoreRepository.findByPlayerId(playerId).stream()
                .map(this::toBlockProgress)
                .toList();

        List<AchievementDto> achievements = playerAchievementRepository.findByPlayerId(playerId).stream()
                .sorted(Comparator.comparing(PlayerAchievement::getEarnedAt).reversed())
                .map(a -> toAchievementDto(a.getAchievementCode(), true, a.getEarnedAt()))
                .toList();

        Long rank = profileOpt.isPresent() ? playerProfileRepository.findRankByPlayerId(playerId) : null;

        PlayerLevel level = PlayerLevel.forScore(totalScore);
        return new ProfileResponse(playerId, displayName, totalScore, scenariosCompleted,
                TOTAL_SCENARIOS_AVAILABLE, blockProgress, achievements, rank,
                level.level(), level.title(), PlayerLevel.progressPercent(totalScore),
                PlayerLevel.pointsToNextLevel(totalScore));
    }

    public LeaderboardResponse getLeaderboard(int limit, UUID requesterId) {
        List<LeaderboardEntryDto> top = playerProfileRepository
                .findByOrderByTotalScoreDesc(PageRequest.of(0, limit))
                .stream()
                .map(profile -> toLeaderboardEntry(profile, requesterId))
                .toList();
        // rank пересчитывается по позиции в уже отсортированном топе — дешевле лишнего запроса.
        top = withRanks(top);

        LeaderboardEntryDto me = null;
        if (requesterId != null) {
            Optional<PlayerProfile> requester = playerProfileRepository.findById(requesterId);
            if (requester.isPresent()) {
                long rank = playerProfileRepository.findRankByPlayerId(requesterId);
                PlayerLevel level = PlayerLevel.forScore(requester.get().getTotalScore());
                me = new LeaderboardEntryDto(rank, playerPublicIdService.publicId(requesterId),
                        displayNameOrGenerated(requester.get()),
                        requester.get().getTotalScore(), requester.get().getScenariosCompleted(), true,
                        level.level(), level.title());
            }
        }
        return new LeaderboardResponse(top, me);
    }

    /** Полный каталог ачивок (см. {@link AchievementCode}) с отметкой полученных для игрока. */
    public List<AchievementDto> getAchievementCatalog(UUID playerId) {
        var earned = playerId == null
                ? List.<PlayerAchievement>of()
                : playerAchievementRepository.findByPlayerId(playerId);
        var earnedByCode = earned.stream()
                .collect(Collectors.toMap(
                        PlayerAchievement::getAchievementCode, PlayerAchievement::getEarnedAt));

        return List.of(AchievementCode.values()).stream()
                .map(code -> toAchievementDto(code, earnedByCode.containsKey(code), earnedByCode.get(code)))
                .toList();
    }

    private List<LeaderboardEntryDto> withRanks(List<LeaderboardEntryDto> ordered) {
        return IntStream.range(0, ordered.size())
                .mapToObj(i -> new LeaderboardEntryDto(i + 1L, ordered.get(i).publicId(),
                        ordered.get(i).displayName(), ordered.get(i).totalScore(),
                        ordered.get(i).scenariosCompleted(), ordered.get(i).me(),
                        ordered.get(i).level(), ordered.get(i).levelTitle()))
                .toList();
    }

    private LeaderboardEntryDto toLeaderboardEntry(PlayerProfile profile, UUID requesterId) {
        PlayerLevel level = PlayerLevel.forScore(profile.getTotalScore());
        return new LeaderboardEntryDto(0, playerPublicIdService.publicId(profile.getId()),
                displayNameOrGenerated(profile), profile.getTotalScore(), profile.getScenariosCompleted(),
                profile.getId().equals(requesterId), level.level(), level.title());
    }

    private BlockProgressDto toBlockProgress(CompetencyScore score) {
        return new BlockProgressDto(score.getBlock(), score.getScenariosCompleted(),
                score.getLoyaltyPoints(), score.getSafetyPoints());
    }

    private AchievementDto toAchievementDto(AchievementCode code, boolean earned, Instant earnedAt) {
        return new AchievementDto(code.name(), code.title(), code.description(), code.category(), earned, earnedAt);
    }

    private String displayNameOrGenerated(PlayerProfile profile) {
        return profile.getDisplayName() != null && !profile.getDisplayName().isBlank()
                ? profile.getDisplayName()
                : generatedDisplayName(profile.getId());
    }

    /** Домена аутентификации нет — короткое имя-заглушка по хвосту id (см. profile.md). */
    private String generatedDisplayName(UUID playerId) {
        String tail = playerId.toString().replace("-", "");
        return "Проводник-" + tail.substring(tail.length() - 4).toUpperCase();
    }
}
