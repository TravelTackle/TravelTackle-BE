package Timeout.travel_tackle.preference.controller;
import Timeout.travel_tackle.preference.service.UserPreferenceService;

import Timeout.travel_tackle.preference.dto.PreferenceRequest;
import Timeout.travel_tackle.preference.dto.PreferenceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/preferences")
@RequiredArgsConstructor
@Tag(name = "Preference", description = "여행 선호도 API")
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    @PostMapping
    @Operation(summary = "여행 선호도 등록 (온보딩)")
    public ResponseEntity<PreferenceResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PreferenceRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userPreferenceService.create(jwt.getSubject(), request));
    }

    @GetMapping
    @Operation(summary = "내 여행 선호도 조회")
    public ResponseEntity<PreferenceResponse> get(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userPreferenceService.get(jwt.getSubject()));
    }

    @PutMapping
    @Operation(summary = "여행 선호도 수정")
    public ResponseEntity<PreferenceResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PreferenceRequest request
    ) {
        return ResponseEntity.ok(userPreferenceService.update(jwt.getSubject(), request));
    }
}
