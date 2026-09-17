package Timeout.travel_tackle.trip.controller;

import Timeout.travel_tackle.trip.dto.*;
import Timeout.travel_tackle.trip.service.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
@Tag(name = "Trip Planning", description = "여행 계획 API")
public class TripController {

    private final TripService tripService;

    @PostMapping
    @Operation(summary = "여행 계획 생성 (기간만큼 TripDay 자동 생성)")
    public ResponseEntity<TripSummaryResponse> createTrip(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTripRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).body(tripService.createTrip(userId, request));
    }

    @GetMapping
    @Operation(summary = "내 여행 계획 목록 조회")
    public ResponseEntity<List<TripSummaryResponse>> getMyTrips(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(tripService.getMyTrips(userId));
    }

    @GetMapping("/{tripId}")
    @Operation(summary = "여행 계획 상세 조회 (일차별 일정 포함)")
    public ResponseEntity<TripDetailResponse> getTripDetail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(tripService.getTripDetail(userId, tripId));
    }

    @PatchMapping("/{tripId}")
    @Operation(summary = "여행 제목·기간 수정 (날짜 변경 시 기존 일정 초기화)")
    public ResponseEntity<TripSummaryResponse> updateTrip(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId,
            @Valid @RequestBody UpdateTripRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(tripService.updateTrip(userId, tripId, request));
    }

    @DeleteMapping("/{tripId}")
    @Operation(summary = "여행 계획 삭제")
    public ResponseEntity<Void> deleteTrip(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        tripService.deleteTrip(userId, tripId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{tripId}/days/{dayId}/items")
    @Operation(summary = "관광지를 일정에 추가 (장바구니 → 시간표 배치)")
    public ResponseEntity<TripItemResponse> addTripItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId,
            @PathVariable UUID dayId,
            @Valid @RequestBody AddTripItemRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tripService.addTripItem(userId, tripId, dayId, request));
    }

    @PatchMapping("/{tripId}/days/{dayId}/items/{itemId}")
    @Operation(summary = "일정 항목 방문 시간 수정 (타임라인 드래그앤드롭)")
    public ResponseEntity<TripItemResponse> updateTripItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId,
            @PathVariable UUID dayId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateTripItemRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(tripService.updateTripItem(userId, tripId, dayId, itemId, request));
    }

    @DeleteMapping("/{tripId}/days/{dayId}/items/{itemId}")
    @Operation(summary = "일정 항목 제거")
    public ResponseEntity<Void> deleteTripItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId,
            @PathVariable UUID dayId,
            @PathVariable UUID itemId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        tripService.deleteTripItem(userId, tripId, dayId, itemId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{tripId}/days/{dayId}/items/reorder")
    @Operation(summary = "같은 날 일정 순서 변경 (드래그앤드롭)")
    public ResponseEntity<List<TripItemResponse>> reorderTripItems(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId,
            @PathVariable UUID dayId,
            @Valid @RequestBody ReorderTripItemsRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(tripService.reorderTripItems(userId, tripId, dayId, request));
    }

    @PatchMapping("/{tripId}/items/{itemId}/move")
    @Operation(summary = "관광지를 다른 날로 이동 (날짜간 드래그앤드롭)")
    public ResponseEntity<TripItemResponse> moveTripItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId,
            @PathVariable UUID itemId,
            @Valid @RequestBody MoveTripItemRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(tripService.moveTripItem(userId, tripId, itemId, request));
    }

    @PatchMapping("/{tripId}/publish")
    @Operation(summary = "여행 계획 공개 (다른 사용자에게 노출/저장 허용)")
    public ResponseEntity<TripSummaryResponse> publishTrip(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(tripService.publishTrip(userId, tripId));
    }

    @PatchMapping("/{tripId}/unpublish")
    @Operation(summary = "여행 계획 비공개 전환")
    public ResponseEntity<TripSummaryResponse> unpublishTrip(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(tripService.unpublishTrip(userId, tripId));
    }
}
