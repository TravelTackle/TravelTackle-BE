package Timeout.travel_tackle.tour.service;

import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import Timeout.travel_tackle.tour.client.TourApiClient;
import Timeout.travel_tackle.tour.client.TourApiClient.TourApiResult;
import Timeout.travel_tackle.tour.dto.TourDtos.Area;
import Timeout.travel_tackle.tour.dto.TourDtos.Category;
import Timeout.travel_tackle.tour.dto.TourDtos.ContentDetail;
import Timeout.travel_tackle.tour.dto.TourDtos.ContentSummary;
import Timeout.travel_tackle.tour.dto.TourDtos.Festival;
import Timeout.travel_tackle.tour.dto.TourDtos.Image;
import Timeout.travel_tackle.tour.dto.TourDtos.Page;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class TourService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_RADIUS_METERS = 20_000;

    private final TourApiClient tourApiClient;

    @Cacheable(cacheNames = "tourAreas", key = "#areaCode ?: 'root'")
    public List<Area> getAreas(String areaCode) {
        return tourApiClient.getAreas(areaCode).items().stream()
                .map(item -> new Area(text(item, "code"), text(item, "name")))
                .toList();
    }

    @Cacheable(cacheNames = "tourCategories")
    public List<Category> getCategories(
            String contentTypeId,
            String category1,
            String category2,
            String category3
    ) {
        return tourApiClient.getCategories(contentTypeId, category1, category2, category3)
                .items().stream()
                .map(item -> new Category(text(item, "code"), text(item, "name")))
                .toList();
    }

    public Page<ContentSummary> getContents(
            String keyword,
            String areaCode,
            String sigunguCode,
            String contentTypeId,
            int page,
            int size,
            String arrange
    ) {
        return getContents(TourLanguageResolver.DEFAULT_SERVICE, keyword, areaCode, sigunguCode, contentTypeId, page, size, arrange);
    }

    /** 언어별 서비스로 지역/키워드 브라우징. */
    @Cacheable(cacheNames = "tourContents")
    public Page<ContentSummary> getContents(
            String service,
            String keyword,
            String areaCode,
            String sigunguCode,
            String contentTypeId,
            int page,
            int size,
            String arrange
    ) {
        validatePage(page, size);
        String normalizedArrange = normalizeArrange(arrange, "A", false);
        TourApiResult result = StringUtils.hasText(keyword)
                ? tourApiClient.searchContents(service, keyword.trim(), areaCode, sigunguCode,
                contentTypeId, page, size, normalizedArrange)
                : tourApiClient.getAreaContents(service, areaCode, sigunguCode,
                contentTypeId, page, size, normalizedArrange);
        return toPage(result);
    }

    @Cacheable(cacheNames = "tourNearby")
    public Page<ContentSummary> getNearbyContents(
            double longitude,
            double latitude,
            int radius,
            String contentTypeId,
            int page,
            int size
    ) {
        validatePage(page, size);
        if (radius < 1 || radius > MAX_RADIUS_METERS
                || longitude < -180 || longitude > 180
                || latitude < -90 || latitude > 90) {
            throw new CustomException(ErrorCode.INVALID_TOUR_SEARCH_CONDITION);
        }
        return toPage(tourApiClient.getNearbyContents(
                longitude, latitude, radius, contentTypeId, page, size));
    }

    public ContentDetail getContentDetail(String contentId) {
        return getContentDetail(TourLanguageResolver.DEFAULT_SERVICE, contentId);
    }

    /**
     * 언어별 서비스로 상세 조회. contentId는 언어 서비스마다 다른 값을 가리키므로
     * 캐시 키에 service를 반드시 포함한다 — 안 그러면 서로 다른 서비스가 우연히 같은
     * contentId 문자열을 쓸 때 잘못된 언어의 데이터가 캐시에서 반환될 수 있다.
     */
    @Cacheable(cacheNames = "tourDetails", key = "#service + ':' + #contentId")
    public ContentDetail getContentDetail(String service, String contentId) {
        TourApiResult commonResult = tourApiClient.getCommonDetail(service, contentId);
        if (commonResult.items().isEmpty()) {
            throw new CustomException(ErrorCode.TOUR_CONTENT_NOT_FOUND);
        }

        JsonNode item = commonResult.items().getFirst();
        List<Image> images;
        try {
            images = tourApiClient.getImages(service, contentId).items().stream()
                    .map(this::toImage)
                    .toList();
        } catch (CustomException e) {
            log.warn("detailImage2 unavailable for contentId={}, proceeding without images", contentId);
            images = List.of();
        }

        return new ContentDetail(
                text(item, "contentid"),
                text(item, "contenttypeid"),
                text(item, "title"),
                address(item),
                text(item, "zipcode"),
                text(item, "areacode"),
                text(item, "sigungucode"),
                text(item, "cat1"),
                text(item, "cat2"),
                text(item, "cat3"),
                imageUrl(item),
                decimal(item, "mapx"),
                decimal(item, "mapy"),
                text(item, "tel"),
                text(item, "homepage"),
                text(item, "overview"),
                images,
                text(item, "lclsSystm1"),
                text(item, "lclsSystm2"),
                text(item, "lclsSystm3")
        );
    }

    @Cacheable(cacheNames = "tourFestivals")
    public Page<Festival> getFestivals(
            LocalDate startDate,
            LocalDate endDate,
            String lDongRegnCd,
            int page,
            int size
    ) {
        validatePage(page, size);
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new CustomException(ErrorCode.INVALID_TOUR_SEARCH_CONDITION);
        }

        TourApiResult result = tourApiClient.getFestivals(
                formatDate(startDate),
                endDate == null ? null : formatDate(endDate),
                lDongRegnCd,
                page,
                size
        );
        return new Page<>(
                result.items().stream().map(this::toFestival).toList(),
                result.page(),
                result.size(),
                result.totalCount()
        );
    }

    @Cacheable(cacheNames = "tourStays")
    public Page<ContentSummary> getStays(
            String areaCode,
            String sigunguCode,
            int page,
            int size
    ) {
        validatePage(page, size);
        return toPage(tourApiClient.getStays(areaCode, sigunguCode, page, size));
    }

    @Cacheable(cacheNames = "tourRecommended")
    public List<ContentSummary> getFilteredContents(
            String lDongRegnCd,
            String contentTypeId,
            String lclsSystm1,
            String lclsSystm2,
            int size
    ) {
        return tourApiClient.getFilteredContents(
                        lDongRegnCd, contentTypeId, lclsSystm1, lclsSystm2, 1, size)
                .items().stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * 언어별 서비스(KorService2/EngService2/JpnService2/...)로 키워드 검색.
     * 챗봇 다국어 지원용 — service에 따라 응답 언어가 달라진다(필드 구조는 v2 공통).
     */
    @Cacheable(cacheNames = "tourContentsByLang")
    public List<ContentSummary> searchInLanguage(String service, String keyword, String contentTypeId, int size) {
        validatePage(1, size);
        return tourApiClient.searchContents(service, keyword.trim(), null, null, contentTypeId, 1, size, "A")
                .items().stream().map(this::toSummary).toList();
    }

    /** 언어별 서비스로 지역+분류(areaBasedList2) 검색 — 키워드 없이 지역/유형으로 그라운딩. */
    @Cacheable(cacheNames = "tourFilteredByLang")
    public List<ContentSummary> getFilteredContentsInLanguage(String service, String lDongRegnCd,
                                                              String contentTypeId, String lclsSystm1,
                                                              String lclsSystm2, int size) {
        validatePage(1, size);
        return tourApiClient.getFilteredContents(service, lDongRegnCd, contentTypeId, lclsSystm1, lclsSystm2, 1, size)
                .items().stream().map(this::toSummary).toList();
    }

    /** 언어별 서비스로 축제 검색. */
    @Cacheable(cacheNames = "tourFestivalsByLang")
    public List<Festival> getFestivalsInLanguage(String service, LocalDate startDate, LocalDate endDate,
                                                 String lDongRegnCd, int size) {
        validatePage(1, size);
        return tourApiClient.getFestivals(service, formatDate(startDate),
                        endDate == null ? null : formatDate(endDate), lDongRegnCd, 1, size)
                .items().stream().map(this::toFestival).toList();
    }

    private Page<ContentSummary> toPage(TourApiResult result) {
        return new Page<>(
                result.items().stream().map(this::toSummary).toList(),
                result.page(),
                result.size(),
                result.totalCount()
        );
    }

    private ContentSummary toSummary(JsonNode item) {
        return new ContentSummary(
                text(item, "contentid"),
                text(item, "contenttypeid"),
                text(item, "title"),
                address(item),
                text(item, "areacode"),
                text(item, "sigungucode"),
                text(item, "cat1"),
                text(item, "cat2"),
                text(item, "cat3"),
                imageUrl(item),
                decimal(item, "mapx"),
                decimal(item, "mapy"),
                text(item, "tel")
        );
    }

    private Image toImage(JsonNode item) {
        return new Image(
                text(item, "originimgurl"),
                text(item, "smallimageurl"),
                text(item, "imgname"),
                text(item, "cpyrhtDivCd")
        );
    }

    private Festival toFestival(JsonNode item) {
        return new Festival(
                text(item, "contentid"),
                text(item, "title"),
                address(item),
                text(item, "areacode"),
                imageUrl(item),
                decimal(item, "mapx"),
                decimal(item, "mapy"),
                parseDate(text(item, "eventstartdate")),
                parseDate(text(item, "eventenddate"))
        );
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_TOUR_SEARCH_CONDITION);
        }
    }

    private String normalizeArrange(String arrange, String defaultValue, boolean nearby) {
        if (!StringUtils.hasText(arrange)) {
            return defaultValue;
        }
        String value = arrange.trim().toUpperCase();
        String allowed = nearby ? "ACDEOQRS" : "ACDOQR";
        if (value.length() != 1 || !allowed.contains(value)) {
            throw new CustomException(ErrorCode.INVALID_TOUR_SEARCH_CONDITION);
        }
        return value;
    }

    private String address(JsonNode item) {
        return Stream.of(text(item, "addr1"), text(item, "addr2"))
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + " " + right)
                .orElse(null);
    }

    private String imageUrl(JsonNode item) {
        String firstImage = text(item, "firstimage");
        return StringUtils.hasText(firstImage) ? firstImage : text(item, "firstimage2");
    }

    private String text(JsonNode item, String field) {
        String value = item.path(field).asText(null);
        return StringUtils.hasText(value) ? value : null;
    }

    private Double decimal(JsonNode item, String field) {
        String value = text(item, field);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String formatDate(LocalDate date) {
        return date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
