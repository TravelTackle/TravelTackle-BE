package Timeout.travel_tackle.trip.controller;

import Timeout.travel_tackle.cart.service.CartService.CartItemResponse;
import Timeout.travel_tackle.trip.dto.CreateFeedbackRequest;
import Timeout.travel_tackle.trip.dto.FeedbackResponse;
import Timeout.travel_tackle.trip.dto.ReceivedFeedbackSummary;
import Timeout.travel_tackle.trip.dto.UpdateFeedbackRequest;
import Timeout.travel_tackle.trip.service.TripFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Trip Feedback", description = "여행 계획 피드백 API")
public class TripFeedbackController {

    private static final int MAX_PAGE_SIZE = 50;

    private final TripFeedbackService feedbackService;

    @PostMapping("/api/trips/{tripId}/feedback")
    @Operation(summary = "피드백 작성 (전체 / 일차 / 아이템 단위)")
    public ResponseEntity<FeedbackResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId,
            @Valid @RequestBody CreateFeedbackRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(feedbackService.create(userId, tripId, request));
    }

    @GetMapping("/api/trips/{tripId}/feedback")
    @Operation(summary = "피드백 목록 조회 (dayId/itemId 파라미터로 단위 필터)")
    public ResponseEntity<Page<FeedbackResponse>> getList(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId,
            @RequestParam(required = false) UUID dayId,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        UUID callerId = jwt != null ? UUID.fromString(jwt.getSubject()) : null;
        return ResponseEntity.ok(feedbackService.getList(tripId, dayId, itemId, callerId, pageable));
    }

    @GetMapping("/api/trips/{tripId}/feedback/all")
    @Operation(summary = "피드백 전체 조회 (레벨 구분 없이, 하이라이트/지도용)")
    public ResponseEntity<Page<FeedbackResponse>> getAll(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        UUID callerId = jwt != null ? UUID.fromString(jwt.getSubject()) : null;
        return ResponseEntity.ok(feedbackService.getAll(tripId, callerId, pageable));
    }

    @PatchMapping("/api/trips/{tripId}/feedback/{feedbackId}")
    @Operation(summary = "피드백 수정 (본인만, 추천 관광지 전체 교체)")
    public ResponseEntity<FeedbackResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId,
            @PathVariable UUID feedbackId,
            @Valid @RequestBody UpdateFeedbackRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(feedbackService.update(userId, tripId, feedbackId, request));
    }

    @DeleteMapping("/api/trips/{tripId}/feedback/{feedbackId}")
    @Operation(summary = "피드백 삭제 (본인 또는 Trip 소유자)")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId,
            @PathVariable UUID feedbackId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        feedbackService.delete(userId, tripId, feedbackId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/trips/{tripId}/feedback/{feedbackId}/like")
    @Operation(summary = "참견에 좋아요 남기기")
    public ResponseEntity<FeedbackResponse> like(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId,
            @PathVariable UUID feedbackId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(feedbackService.likeFeedback(userId, tripId, feedbackId));
    }

    @DeleteMapping("/api/trips/{tripId}/feedback/{feedbackId}/like")
    @Operation(summary = "참견 좋아요 취소")
    public ResponseEntity<FeedbackResponse> unlike(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId,
            @PathVariable UUID feedbackId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(feedbackService.unlikeFeedback(userId, tripId, feedbackId));
    }

    @PostMapping("/api/trips/{tripId}/feedback/recommendations/{recommendationId}/cart")
    @Operation(summary = "피드백 추천 장소를 내 카트에 담기 (Trip 소유자 전용)")
    public ResponseEntity<CartItemResponse> addRecommendationToCart(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId,
            @PathVariable UUID recommendationId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(feedbackService.addRecommendationToCart(userId, tripId, recommendationId));
    }

    @PostMapping("/api/trips/{tripId}/feedback/notifications/dismiss")
    @Operation(summary = "참견 알림함 지우기 (읽음 처리 + 지운 시각 저장, 새 참견 달리면 다시 노출)")
    public ResponseEntity<Void> dismissNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        feedbackService.dismissNotifications(userId, tripId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/trips/feedback/received")
    @Operation(summary = "내 여행 계획에 달린 피드백 모아보기 (미읽음 수 포함)")
    public ResponseEntity<List<ReceivedFeedbackSummary>> getReceived(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(feedbackService.getReceivedSummary(userId));
    }
}
