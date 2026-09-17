package Timeout.travel_tackle.notification.dto;

import Timeout.travel_tackle.entity.Enum.NotificationTarget;

import java.util.UUID;

/** 참견 알림 생성 요청. trip 패키지가 필요한 값을 모두 스냅샷으로 넘겨 notification 패키지가 trip 에 의존하지 않게 한다. */
public record FeedbackNotificationCommand(
        UUID receiverId,
        UUID actorId,
        String actorName,
        UUID tripId,
        String tripTitle,
        String thumbnailUrl,
        UUID feedbackId,
        NotificationTarget target,
        Integer dayNumber,
        String itemTitle,
        String content
) {
}
