package Timeout.travel_tackle.tour.dto;

import java.time.LocalDate;
import java.util.List;

public final class TourDtos {

    private TourDtos() {
    }

    public record Area(String code, String name) {
    }

    public record Category(String code, String name) {
    }

    public record ContentSummary(
            String contentId,
            String contentTypeId,
            String title,
            String address,
            String areaCode,
            String sigunguCode,
            String category1,
            String category2,
            String category3,
            String imageUrl,
            Double longitude,
            Double latitude,
            String telephone
    ) {
    }

    public record Image(
            String originalUrl,
            String thumbnailUrl,
            String name,
            String copyrightType
    ) {
    }

    public record Festival(
            String contentId,
            String title,
            String address,
            String areaCode,
            String imageUrl,
            Double longitude,
            Double latitude,
            LocalDate startDate,
            LocalDate endDate
    ) {
    }

    public record ContentDetail(
            String contentId,
            String contentTypeId,
            String title,
            String address,
            String zipCode,
            String areaCode,
            String sigunguCode,
            String category1,
            String category2,
            String category3,
            String imageUrl,
            Double longitude,
            Double latitude,
            String telephone,
            String homepage,
            String overview,
            List<Image> images,
            String lclsSystm1,
            String lclsSystm2,
            String lclsSystm3
    ) {
    }

    public record Page<T>(
            List<T> items,
            int page,
            int size,
            int totalCount
    ) {
    }
}
