package Timeout.travel_tackle.notification.dto;

/** SSE 로 보내는 새 알림 이벤트. 토스트 표시용 본문과 종 아이콘 갱신용 미읽음 수를 함께 싣는다. */
public record NotificationPushEvent(NotificationResponse notification, long unreadCount) {
}
