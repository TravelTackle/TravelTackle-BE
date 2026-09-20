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
        List<String> photoUrls,
        long feedbackCount,
        long saveCount,
        UUID savedTripId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<TripDayResponse> days,
        PetFriendlySummary petFriendly // 계획의 장소 기준. 기록 카드도 같은 계획 값을 싣는다
) {
    public static FeedItemResponse ofPlan(
            Trip trip, String thumbnailUrl, long feedbackCount, long saveCount, UUID savedTripId, String region,
            List<TripDayResponse> days
    ) {
        return new FeedItemResponse(
                trip.getId(),
                FeedItemType.PLAN,
                trip.getTitle(),
                trip.getComment(),
                region,
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getStatus(),
                trip.getUser().getId(),
                trip.getUser().getName(),
                trip.getUser().getProfileImageUrl(),
                thumbnailUrl,
                List.of(),
                feedbackCount,
                saveCount,
                savedTripId,
                trip.getCreatedAt(),
                trip.getUpdatedAt(),
                days,
                PetFriendlySummary.of(days)
        );
    }

    public static FeedItemResponse ofRecord(
            Trip trip, TripRecord record, String thumbnailUrl, List<String> photoUrls, long feedbackCount, long saveCount, UUID savedTripId,
            String region, PetFriendlySummary petFriendly
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
                photoUrls,
                feedbackCount,
                saveCount,
                savedTripId,
                record.getCreatedAt(),
                null,
                null,
                petFriendly
        );
    }
}
