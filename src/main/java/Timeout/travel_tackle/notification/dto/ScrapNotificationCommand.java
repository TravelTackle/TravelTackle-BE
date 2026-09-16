package Timeout.travel_tackle.notification.dto;

import java.util.UUID;

/** 스크랩 알림 생성 요청. 참견 알림과 마찬가지로 trip 패키지가 스냅샷 값을 넘긴다. */
public record ScrapNotificationCommand(
        UUID receiverId,
        UUID actorId,
        String actorName,
        UUID tripId,
        String tripTitle,
        String thumbnailUrl
) {
}
