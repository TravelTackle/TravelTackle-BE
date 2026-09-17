package Timeout.travel_tackle.trip.recommendation.dto;

import Timeout.travel_tackle.entity.Enum.TripStatus;
import Timeout.travel_tackle.entity.Trip;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 추천 탭 - 계획 추천. 내 선호도와 일치하는 방문지가 많은 공개 계획.
 * matchScore가 높을수록 취향에 더 맞는 계획.
 */
public record RecommendedTripResponse(
        UUID tripId,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        TripStatus status,
        String ownerName,
        String thumbnailUrl,
        LocalDateTime createdAt,
        int matchScore
) {
    public static RecommendedTripResponse of(Trip trip, String thumbnailUrl, int matchScore) {
        return new RecommendedTripResponse(
                trip.getId(),
                trip.getTitle(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getStatus(),
                trip.getUser().getName(),
                thumbnailUrl,
                trip.getCreatedAt(),
                matchScore
        );
    }
}
