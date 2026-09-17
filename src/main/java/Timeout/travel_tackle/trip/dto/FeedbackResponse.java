package Timeout.travel_tackle.trip.dto;

import Timeout.travel_tackle.entity.TripFeedback;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record FeedbackResponse(
        UUID id,
        AuthorInfo author,
        String content,
        UUID tripDayId,
        UUID tripItemId,
        List<FeedbackRecommendationResponse> recommendations,
        boolean read,
        long likeCount,
        boolean likedByMe,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record AuthorInfo(UUID id, String name, String profileImageUrl) {}

    public static FeedbackResponse of(
            TripFeedback feedback, List<FeedbackRecommendationResponse> recommendations,
            long likeCount, boolean likedByMe
    ) {
        AuthorInfo author = feedback.getAuthor() != null
                ? new AuthorInfo(feedback.getAuthor().getId(), feedback.getAuthor().getName(),
                        feedback.getAuthor().getProfileImageUrl())
                : new AuthorInfo(null, "탈퇴한 사용자", null);
        return new FeedbackResponse(
                feedback.getId(),
                author,
                feedback.getContent(),
                feedback.getTripDay() != null ? feedback.getTripDay().getId() : null,
                feedback.getTripItem() != null ? feedback.getTripItem().getId() : null,
                recommendations,
                feedback.isRead(),
                likeCount,
                likedByMe,
                feedback.getCreatedAt(),
                feedback.getUpdatedAt()
        );
    }
}
