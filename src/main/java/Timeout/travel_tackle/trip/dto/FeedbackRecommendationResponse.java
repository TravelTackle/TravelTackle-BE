package Timeout.travel_tackle.trip.dto;

import Timeout.travel_tackle.entity.TripFeedbackRecommendation;

import java.util.UUID;

public record FeedbackRecommendationResponse(
        UUID id,
        String contentId,
        String title,
        String imageUrl,
        String areaCode
) {
    public static FeedbackRecommendationResponse from(TripFeedbackRecommendation rec) {
        return new FeedbackRecommendationResponse(
                rec.getId(),
                rec.getTourApiContentId(),
                rec.getCachedTitle(),
                rec.getCachedImageUrl(),
                rec.getCachedAreaCode()
        );
    }
}
