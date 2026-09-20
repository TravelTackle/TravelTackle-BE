package Timeout.travel_tackle.tour.controller;

import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import Timeout.travel_tackle.tour.dto.RecommendationDtos.RecommendationsResponse;
import Timeout.travel_tackle.tour.dto.TourDtos.Area;
import Timeout.travel_tackle.tour.dto.TourDtos.Category;
import Timeout.travel_tackle.tour.dto.TourDtos.ContentDetail;
import Timeout.travel_tackle.tour.dto.TourDtos.ContentSummary;
import Timeout.travel_tackle.tour.dto.TourDtos.Festival;
import Timeout.travel_tackle.tour.dto.TourDtos.Page;
import Timeout.travel_tackle.tour.recommendation.service.RecommendationService;
import Timeout.travel_tackle.tour.service.TourService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/tour")
@RequiredArgsConstructor
@Tag(name = "Tour", description = "한국관광공사 관광정보 API")
public class TourController {

    private final TourService tourService;
    private final RecommendationService recommendationService;

    @GetMapping("/recommended")
    @Operation(summary = "선호도 기반 섹션 추천")
    public RecommendationsResponse getRecommendations(@AuthenticationPrincipal Jwt jwt) {
        // GET /api/tour/** is permitAll, so unauthenticated requests reach here with a null principal.
        if (jwt == null) {
            throw new CustomException(ErrorCode.UNAUTHENTICATED);
        }
        return recommendationService.getRecommendations(jwt.getSubject());
    }

    @GetMapping("/areas")
    @Operation(summary = "지역 또는 시군구 코드 조회")
    public List<Area> getAreas(@RequestParam(required = false) String areaCode) {
        return tourService.getAreas(areaCode);
    }

    @GetMapping("/categories")
    @Operation(summary = "관광 콘텐츠 분류코드 조회")
    public List<Category> getCategories(
            @RequestParam(required = false) String contentTypeId,
            @RequestParam(required = false) String category1,
            @RequestParam(required = false) String category2,
            @RequestParam(required = false) String category3
    ) {
        return tourService.getCategories(contentTypeId, category1, category2, category3);
    }

    @GetMapping("/contents")
    @Operation(summary = "지역별 관광 콘텐츠 조회 및 키워드 검색",
            description = "petFriendly=true 면 반려동물 동반 가능 장소만 (한국관광공사 반려동물 동반여행 서비스). contentId 는 일반 검색과 동일")
    public Page<ContentSummary> getContents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String areaCode,
            @RequestParam(required = false) String sigunguCode,
            @RequestParam(required = false) String contentTypeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "A") String arrange,
            @RequestParam(defaultValue = "false") boolean petFriendly
    ) {
        return tourService.getContents(
                keyword, areaCode, sigunguCode, contentTypeId, page, size, arrange, petFriendly);
    }

    @GetMapping("/contents/nearby")
    @Operation(summary = "좌표 기반 주변 관광 콘텐츠 조회 (petFriendly=true 면 반려동물 동반 가능 장소만)")
    public Page<ContentSummary> getNearbyContents(
            @RequestParam double longitude,
            @RequestParam double latitude,
            @RequestParam(defaultValue = "5000") int radius,
            @RequestParam(required = false) String contentTypeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean petFriendly
    ) {
        return tourService.getNearbyContents(
                longitude, latitude, radius, contentTypeId, page, size, petFriendly);
    }

    @GetMapping("/contents/{contentId}")
    @Operation(summary = "관광 콘텐츠 상세 및 이미지 조회 (반려동물 동반 안내 petInfo 포함, 미등록 장소는 null)")
    public ContentDetail getContentDetail(@PathVariable String contentId) {
        return tourService.getContentDetail(contentId);
    }

    @GetMapping("/contents/{contentId}/related")
    @Operation(summary = "연관 관광지 조회",
            description = "티맵 이동 데이터 기반 연관 순위 순으로 관광지를 반환한다. 음식점·숙박이거나 연관 데이터가 없으면 빈 배열.")
    public List<ContentSummary> getRelatedContents(
            @PathVariable String contentId,
            @RequestParam(defaultValue = "8") int limit
    ) {
        return tourService.getRelatedContents(contentId, limit);
    }

    @GetMapping("/festivals")
    @Operation(summary = "기간별 축제·행사 조회")
    public Page<Festival> getFestivals(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String lDongRegnCd,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return tourService.getFestivals(startDate, endDate, lDongRegnCd, page, size);
    }

    @GetMapping("/stays")
    @Operation(summary = "지역별 숙박 조회")
    public Page<ContentSummary> getStays(
            @RequestParam(required = false) String areaCode,
            @RequestParam(required = false) String sigunguCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return tourService.getStays(areaCode, sigunguCode, page, size);
    }
}
