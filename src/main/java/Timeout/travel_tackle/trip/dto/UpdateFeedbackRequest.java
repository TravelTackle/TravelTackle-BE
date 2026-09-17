package Timeout.travel_tackle.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateFeedbackRequest(
        @NotBlank @Size(max = 2000) String content,
        List<CreateFeedbackRequest.RecommendationRequest> recommendations
) {}
