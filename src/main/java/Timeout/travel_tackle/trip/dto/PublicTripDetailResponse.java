package Timeout.travel_tackle.trip.dto;

import Timeout.travel_tackle.entity.Enum.TripStatus;
import Timeout.travel_tackle.entity.Trip;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PublicTripDetailResponse(
        UUID id,
        String title,
        String comment,
        String region,
        LocalDate startDate,
        LocalDate endDate,
        TripStatus status,
        UUID ownerId,
        String ownerName,
        String ownerProfileImageUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<TripDayResponse> days,
        TripRecordResponse record,
        long feedbackCount,
        UUID savedTripId,
        long saveCount,
        PetFriendlySummary petFriendly
) {
    public static PublicTripDetailResponse of(
            Trip trip,
            String region,
            List<TripDayResponse> days,
            TripRecordResponse record,
            long feedbackCount,
            UUID savedTripId,
            long saveCount
    ) {
        return new PublicTripDetailResponse(
                trip.getId(),
                trip.getTitle(),
                trip.getComment(),
                region,
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getStatus(),
                trip.getUser().getId(),
                trip.getUser().getName(),
                trip.getUser().getProfileImageUrl(),
                trip.getCreatedAt(),
                trip.getUpdatedAt(),
                days,
                record,
                feedbackCount,
                savedTripId,
                saveCount,
                PetFriendlySummary.of(days)
        );
    }
}
