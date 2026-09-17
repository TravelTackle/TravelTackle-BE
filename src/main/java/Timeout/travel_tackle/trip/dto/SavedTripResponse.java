package Timeout.travel_tackle.trip.dto;

import Timeout.travel_tackle.entity.Enum.FeedItemType;
import Timeout.travel_tackle.entity.SavedTrip;
import Timeout.travel_tackle.entity.TripRecord;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 보관함(스크랩) 응답 — FeedItemResponse와 같은 모양의 카드 데이터를 낸다.
 * sourceType=PLAN이면 원본 계획의 title/days를, RECORD면 그 계획에 달린 TripRecord의
 * title/content를 담고 days는 null이다. RECORD로 스크랩했는데 그 사이 기록이 지워졌다면
 * {@link #ofRecordFallbackToPlan}으로 PLAN 모양으로 안전하게 내려준다.
 */
public record SavedTripResponse(
        UUID savedTripId,
        UUID originalTripId,
        FeedItemType sourceType,
        String title,
        String content,
        String ownerName,
        String ownerProfileImageUrl,
        String region,
        LocalDate startDate,
        LocalDate endDate,
        String thumbnailUrl,
        long feedbackCount,
        long saveCount,
        UUID copiedTripId,
        LocalDateTime savedAt,
        List<TripDayResponse> days
) {
    public static SavedTripResponse ofPlan(
            SavedTrip savedTrip, String region, String thumbnailUrl, long feedbackCount, long saveCount,
            List<TripDayResponse> days
    ) {
        return new SavedTripResponse(
                savedTrip.getId(),
                savedTrip.getOriginalTrip().getId(),
                FeedItemType.PLAN,
                savedTrip.getOriginalTrip().getTitle(),
                null,
                savedTrip.getOriginalTrip().getUser().getName(),
                savedTrip.getOriginalTrip().getUser().getProfileImageUrl(),
                region,
                savedTrip.getOriginalTrip().getStartDate(),
                savedTrip.getOriginalTrip().getEndDate(),
                thumbnailUrl,
                feedbackCount,
                saveCount,
                savedTrip.getCopiedTrip() != null ? savedTrip.getCopiedTrip().getId() : null,
                savedTrip.getSavedAt(),
                days
        );
    }

    public static SavedTripResponse ofRecord(
            SavedTrip savedTrip, TripRecord record, String region, String thumbnailUrl,
            long feedbackCount, long saveCount
    ) {
        return new SavedTripResponse(
                savedTrip.getId(),
                savedTrip.getOriginalTrip().getId(),
                FeedItemType.RECORD,
                record.getTitle(),
                record.getContent(),
                savedTrip.getOriginalTrip().getUser().getName(),
                savedTrip.getOriginalTrip().getUser().getProfileImageUrl(),
                region,
                savedTrip.getOriginalTrip().getStartDate(),
                savedTrip.getOriginalTrip().getEndDate(),
                thumbnailUrl,
                feedbackCount,
                saveCount,
                savedTrip.getCopiedTrip() != null ? savedTrip.getCopiedTrip().getId() : null,
                savedTrip.getSavedAt(),
                null
        );
    }
}
