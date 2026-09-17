package Timeout.travel_tackle.auth.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 알림 설정 전체 교체(PUT) — 4개 값 모두 필수로 받아 한 번에 덮어쓴다.
 */
public record NotificationSettingsRequest(
        @NotNull Boolean notifyEmail,
        @NotNull Boolean notifyFeedback,
        @NotNull Boolean notifyRecommend,
        @NotNull Boolean notifyEvent
) {
}
