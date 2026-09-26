package ru.vsm.backend.gamification.showcase.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vsm.backend.auth.security.PlayerPublicIdService;
import ru.vsm.backend.gamification.showcase.domain.ShowcaseItem;
import ru.vsm.backend.gamification.showcase.repository.ShowcaseItemRepository;
import ru.vsm.backend.gamification.showcase.web.dto.ShowcaseItemDto;
import ru.vsm.backend.gamification.showcase.web.dto.ShowcaseRequest;
import ru.vsm.backend.gamification.showcase.web.dto.ShowcaseResponse;

/**
 * Витрина наград: игрок сам выбирает до шести наград, коллеги видят их по {@code publicId}.
 * Сервер хранит снимки как есть и не подтверждает факт получения награды — витрина
 * декоративная и на очки/рейтинг не влияет (антифрод рейтинга — в начислениях).
 */
@Service
@RequiredArgsConstructor
public class ShowcaseService {

    private static final String DEFAULT_FINISH = "enamel";

    private final ShowcaseItemRepository repository;
    private final PlayerPublicIdService publicIdService;

    @Transactional
    public ShowcaseResponse replace(UUID playerId, ShowcaseRequest request) {
        String publicId = publicIdService.publicId(playerId);
        String finish = request.finish() == null ? DEFAULT_FINISH : request.finish();
        repository.deleteAllByPlayerId(playerId);
        List<ShowcaseItem> rows = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        int position = 0;
        for (ShowcaseItemDto dto : request.items()) {
            if (seen.contains(dto.id())) {
                continue;
            }
            seen.add(dto.id());
            rows.add(ShowcaseItem.builder()
                    .playerId(playerId)
                    .publicId(publicId)
                    .position(position++)
                    .awardId(dto.id())
                    .title(dto.title())
                    .shape(dto.shape())
                    .glyph(blankToNull(dto.glyph()))
                    .label(blankToNull(dto.text()))
                    .finish(finish)
                    .updatedAt(Instant.now())
                    .build());
        }
        repository.saveAll(rows);
        return toResponse(rows);
    }

    @Transactional(readOnly = true)
    public ShowcaseResponse byPublicId(String publicId) {
        return toResponse(repository.findByPublicIdOrderByPositionAsc(publicId));
    }

    private static ShowcaseResponse toResponse(List<ShowcaseItem> rows) {
        String finish = rows.isEmpty() ? DEFAULT_FINISH : rows.getFirst().getFinish();
        List<ShowcaseItemDto> items = rows.stream()
                .map(r -> new ShowcaseItemDto(r.getAwardId(), r.getTitle(), r.getShape(), r.getGlyph(), r.getLabel()))
                .toList();
        return new ShowcaseResponse(finish, items);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
