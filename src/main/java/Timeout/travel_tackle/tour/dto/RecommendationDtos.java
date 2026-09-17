package Timeout.travel_tackle.tour.dto;

import Timeout.travel_tackle.tour.dto.TourDtos.ContentSummary;

import java.util.List;

public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    public record RecommendedSection(
            String sectionId,
            String title,
            List<ContentSummary> items
    ) {
    }

    public record RecommendationsResponse(
            List<RecommendedSection> sections
    ) {
    }
}
