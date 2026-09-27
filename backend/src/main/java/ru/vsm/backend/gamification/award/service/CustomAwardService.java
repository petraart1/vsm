package ru.vsm.backend.gamification.award.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.vsm.backend.auth.repository.AppUserRepository;
import ru.vsm.backend.auth.service.PlayerVerificationService;
import ru.vsm.backend.gamification.award.domain.CustomAward;
import ru.vsm.backend.gamification.award.domain.CustomAwardGrant;
import ru.vsm.backend.gamification.award.repository.CustomAwardGrantRepository;
import ru.vsm.backend.gamification.award.repository.CustomAwardRepository;
import ru.vsm.backend.gamification.award.web.dto.CustomAwardDto;
import ru.vsm.backend.gamification.award.web.dto.CustomAwardRequest;

/** Награды администратора: создание, выдача игроку и каталог с отметкой полученных. */
@Service
@RequiredArgsConstructor
public class CustomAwardService {

    private final CustomAwardRepository awardRepository;
    private final CustomAwardGrantRepository grantRepository;
    private final AppUserRepository appUserRepository;
    private final PlayerVerificationService verificationService;

    @Transactional
    public CustomAwardDto create(CustomAwardRequest request) {
        CustomAward award = awardRepository.save(CustomAward.builder()
                .code("award-" + UUID.randomUUID().toString().substring(0, 8))
                .title(request.title().trim())
                .description(request.description().trim())
                .shape(request.shape())
                .glyph(request.glyph() == null || request.glyph().isBlank() ? null : request.glyph())
                .verifiedOnly(request.verifiedOnly())
                .build());
        return toDto(award, null, 0);
    }

    @Transactional(readOnly = true)
    public List<CustomAwardDto> listForAdmin() {
        return awardRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(a -> toDto(a, null, grantRepository.countByAwardId(a.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomAwardDto> catalogFor(UUID playerId) {
        Map<UUID, CustomAwardGrant> grants = playerId == null ? Map.of()
                : grantRepository.findByPlayerId(playerId).stream()
                        .collect(Collectors.toMap(CustomAwardGrant::getAwardId, Function.identity(), (a, b) -> a));
        return awardRepository.findAll().stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .map(a -> toDto(a, grants.get(a.getId()), 0))
                .toList();
    }

    /** Выдать награду игроку по логину учётной записи или по playerId (UUID). */
    @Transactional
    public CustomAwardDto grant(UUID awardId, String player) {
        CustomAward award = awardRepository.findById(awardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Награда не найдена"));
        UUID playerId = resolvePlayer(player.trim());
        if (award.isVerifiedOnly() && !verificationService.isVerified(playerId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Награда только для подтверждённых через Госуслуги учётных записей");
        }
        if (!grantRepository.existsByAwardIdAndPlayerId(awardId, playerId)) {
            grantRepository.save(CustomAwardGrant.builder().awardId(awardId).playerId(playerId).grantedAt(Instant.now()).build());
        }
        return toDto(award, null, grantRepository.countByAwardId(awardId));
    }

    private UUID resolvePlayer(String player) {
        try {
            return UUID.fromString(player);
        } catch (IllegalArgumentException e) {
            return appUserRepository.findByLogin(player)
                    .map(u -> u.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Учётная запись не найдена: " + player));
        }
    }

    private static CustomAwardDto toDto(CustomAward a, CustomAwardGrant grant, long grantedCount) {
        return new CustomAwardDto(a.getId(), a.getCode(), a.getTitle(), a.getDescription(), a.getShape(), a.getGlyph(),
                a.isVerifiedOnly(), grant != null, grant == null ? null : grant.getGrantedAt(), grantedCount);
    }
}
