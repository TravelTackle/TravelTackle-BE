package Timeout.travel_tackle.trip.dto;

import Timeout.travel_tackle.entity.Enum.TripStatus;
import Timeout.travel_tackle.entity.Trip;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TripSummaryResponse(
        UUID id,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        TripStatus status,
        boolean published,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TripSummaryResponse from(Trip trip) {
        return new TripSummaryResponse(
                trip.getId(),
                trip.getTitle(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getStatus(),
                trip.isPublished(),
                trip.getComment(),
                trip.getCreatedAt(),
                trip.getUpdatedAt()
        );
    }
}
