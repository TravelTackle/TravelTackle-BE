package Timeout.travel_tackle.notification.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record NotificationPageResponse(
        long unreadCount,
        List<NotificationResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static NotificationPageResponse of(long unreadCount, Page<NotificationResponse> page) {
        return new NotificationPageResponse(unreadCount, page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
