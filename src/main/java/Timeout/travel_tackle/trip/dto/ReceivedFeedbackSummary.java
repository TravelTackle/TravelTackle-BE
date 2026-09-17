package Timeout.travel_tackle.trip.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReceivedFeedbackSummary(
        UUID tripId,
        String tripTitle,
        long totalFeedbackCount,
        long unreadCount,
        LocalDateTime latestFeedbackAt
) {}
