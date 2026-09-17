package Timeout.travel_tackle.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateFeedbackRequest(
        @NotBlank @Size(max = 2000) String content,
        UUID tripDayId,
        UUID tripItemId,
        List<RecommendationRequest> recommendations
) {
    public record RecommendationRequest(String contentId) {}
}
