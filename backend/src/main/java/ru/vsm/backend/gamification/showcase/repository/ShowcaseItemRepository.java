package ru.vsm.backend.gamification.showcase.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.vsm.backend.gamification.showcase.domain.ShowcaseItem;

public interface ShowcaseItemRepository extends JpaRepository<ShowcaseItem, UUID> {

    List<ShowcaseItem> findByPublicIdOrderByPositionAsc(String publicId);

    List<ShowcaseItem> findByPlayerIdOrderByPositionAsc(UUID playerId);

    @Modifying
    @Query("delete from ShowcaseItem s where s.playerId = :playerId")
    void deleteAllByPlayerId(@Param("playerId") UUID playerId);
}
