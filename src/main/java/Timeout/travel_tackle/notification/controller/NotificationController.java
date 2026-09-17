package Timeout.travel_tackle.notification.controller;

import Timeout.travel_tackle.global.util.UuidConverter;
import Timeout.travel_tackle.notification.dto.NotificationPageResponse;
import Timeout.travel_tackle.notification.dto.UnreadCountResponse;
import Timeout.travel_tackle.notification.service.NotificationService;
import Timeout.travel_tackle.notification.sse.NotificationSseRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "알림 API — 목록/미읽음 수/읽음 처리 + SSE 실시간 푸시")
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationService notificationService;
    private final NotificationSseRegistry sseRegistry;

    @GetMapping
    @Operation(summary = "내 알림 목록 (최신순) + 미읽음 수")
    public ResponseEntity<NotificationPageResponse> getNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return ResponseEntity.ok(notificationService.getNotifications(
                UuidConverter.fromSubject(jwt.getSubject()), PageRequest.of(Math.max(page, 0), safeSize)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "미읽음 알림 수 (종 아이콘)")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(notificationService.getUnreadCount(UuidConverter.fromSubject(jwt.getSubject())));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "알림 하나 읽음 처리")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID notificationId) {
        notificationService.markRead(UuidConverter.fromSubject(jwt.getSubject()), notificationId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    @Operation(summary = "알림 전체 읽음 처리")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        notificationService.markAllRead(UuidConverter.fromSubject(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "실시간 알림 SSE 연결",
            description = "event: notification (새 알림 + unreadCount), unread-count (읽음 처리 후 갱신), heartbeat (25초)")
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt) {
        return sseRegistry.connect(UuidConverter.fromSubject(jwt.getSubject()));
    }
}
