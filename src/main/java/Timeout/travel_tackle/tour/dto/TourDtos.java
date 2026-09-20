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
            String lclsSystm3,
            PetTourInfo petInfo // 반려동물 동반 안내. 반려동물 동반 서비스에 등록되지 않은 장소는 null
    ) {
    }

    /** 반려동물 동반 안내 (KorPetTourService2 detailPetTour2). 값이 없는 항목은 null */
    public record PetTourInfo(
            String companionType,     // 동반 유형 (예: 전구역 동반가능)
            String allowedAnimals,    // 동반 가능 동물
            String requirements,      // 동반 시 필요사항 (예: 목줄 착용)
            String notes,             // 기타 동반 정보
            String facilities,        // 관련 구비 시설
            String providedItems,     // 비치 품목
            String purchasableItems,  // 구매 가능 품목
            String rentalItems,       // 렌탈 품목
            String safetyNotes        // 관련 사고 대비사항
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
