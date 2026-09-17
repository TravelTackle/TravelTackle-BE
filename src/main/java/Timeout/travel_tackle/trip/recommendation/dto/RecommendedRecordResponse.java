package Timeout.travel_tackle.trip.recommendation.dto;

import Timeout.travel_tackle.entity.TripRecord;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 추천 탭 - 기록 추천. 내 선호도와 일치하는 방문지가 많은 계획에 달린 공개 기록(후기).
 * 계획 추천과 분리된 별도 목록. matchScore는 기록이 속한 계획의 취향 일치 점수.
 */
public record RecommendedRecordResponse(
        UUID recordId,
        UUID tripId,
        String tripTitle,
        String ownerName,
        String content,
        String thumbnailUrl,
        LocalDateTime createdAt,
        int matchScore
) {
    public static RecommendedRecordResponse of(TripRecord record, String thumbnailUrl, int matchScore) {
        return new RecommendedRecordResponse(
                record.getId(),
                record.getTrip().getId(),
                record.getTrip().getTitle(),
                record.getTrip().getUser().getName(),
                record.getContent(),
                thumbnailUrl,
                record.getCreatedAt(),
                matchScore
        );
    }
}
