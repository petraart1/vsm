package ru.vsm.backend.gamification.award.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.gamification.award.service.CustomAwardService;
import ru.vsm.backend.gamification.award.web.dto.CustomAwardDto;
import ru.vsm.backend.gamification.award.web.dto.CustomAwardGrantRequest;
import ru.vsm.backend.gamification.award.web.dto.CustomAwardRequest;

/** Админ-панель: награды — список, создание, выдача игроку. Только роль ADMIN ({@code /api/admin/**}). */
@RestController
@RequestMapping("/api/admin/awards")
@RequiredArgsConstructor
public class AdminCustomAwardController {

    private final CustomAwardService service;

    @GetMapping
    public List<CustomAwardDto> list() {
        return service.listForAdmin();
    }

    @PostMapping
    public ResponseEntity<CustomAwardDto> create(@Valid @RequestBody CustomAwardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/{id}/grant")
    public CustomAwardDto grant(@PathVariable UUID id, @Valid @RequestBody CustomAwardGrantRequest request) {
        return service.grant(id, request.player());
    }
}
