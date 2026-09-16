package Timeout.travel_tackle.trip.dto;

import Timeout.travel_tackle.entity.Enum.FeedItemType;
import Timeout.travel_tackle.entity.Enum.TripStatus;
import Timeout.travel_tackle.entity.Trip;
import Timeout.travel_tackle.entity.TripRecord;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record FeedItemResponse(
        UUID tripId,
        FeedItemType type,
        String title,
        String content,
        String region,
        LocalDate startDate,
        LocalDate endDate,
        TripStatus status,
        UUID ownerId,
        String ownerName,
        String ownerProfileImageUrl,
        String thumbnailUrl,
        long feedbackCount,
        long saveCount,
        UUID savedTripId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<TripDayResponse> days
) {
    public static FeedItemResponse ofPlan(
            Trip trip, String thumbnailUrl, long feedbackCount, long saveCount, UUID savedTripId, String region,
            List<TripDayResponse> days
    ) {
        return new FeedItemResponse(
                trip.getId(),
                FeedItemType.PLAN,
                trip.getTitle(),
                null,
                region,
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getStatus(),
                trip.getUser().getId(),
                trip.getUser().getName(),
                trip.getUser().getProfileImageUrl(),
                thumbnailUrl,
                feedbackCount,
                saveCount,
                savedTripId,
                trip.getCreatedAt(),
                trip.getUpdatedAt(),
                days
        );
    }

    public static FeedItemResponse ofRecord(
            Trip trip, TripRecord record, String thumbnailUrl, long feedbackCount, long saveCount, UUID savedTripId,
            String region
    ) {
        return new FeedItemResponse(
                trip.getId(),
                FeedItemType.RECORD,
                record.getTitle(),
                record.getContent(),
                region,
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getStatus(),
                trip.getUser().getId(),
                trip.getUser().getName(),
                trip.getUser().getProfileImageUrl(),
                thumbnailUrl,
                feedbackCount,
                saveCount,
                savedTripId,
                record.getCreatedAt(),
                null,
                null
        );
    }
}
