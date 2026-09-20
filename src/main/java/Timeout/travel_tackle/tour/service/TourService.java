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
import java.time.YearMonth;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class TourService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_RADIUS_METERS = 20_000;

    private static final int MAX_RELATED_LIMIT = 8;
    // TourAPI 미등록 항목(상업시설·교통시설 등)이 약 40% 빠지므로 노출 수보다 넉넉히 후보를 잡는다
    private static final int RELATED_CANDIDATES = 16;
    private static final int RELATED_FETCH_SIZE = 50; // 연관 API의 관광지당 최대 제공 건수
    private static final String RELATED_CATEGORY = "관광지";
    private static final Set<String> NO_RELATED_TYPES = Set.of("39", "32"); // 음식점, 숙박
    private static final ExecutorService RELATED_MATCHER = Executors.newVirtualThreadPerTaskExecutor();

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

    @Cacheable(cacheNames = "tourContents")
    public Page<ContentSummary> getContents(
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
                ? tourApiClient.searchContents(keyword.trim(), areaCode, sigunguCode,
                contentTypeId, page, size, normalizedArrange)
                : tourApiClient.getAreaContents(areaCode, sigunguCode,
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

    /**
     * 관광지의 연관 관광지를 연관 순위순으로 반환한다. 연관 API는 자체 코드/이름만 주므로
     * 이름으로 TourAPI를 다시 검색해 contentId를 붙이고, 매칭되지 않는 항목은 건너뛴다.
     * 음식점·숙박이거나 연관 데이터가 없으면 빈 목록(호출 측에서 주변 관광지로 대체).
     */
    @Cacheable(cacheNames = "tourRelated", key = "#contentId + ':' + #limit")
    public List<ContentSummary> getRelatedContents(String contentId, int limit) {
        if (limit < 1 || limit > MAX_RELATED_LIMIT) {
            throw new CustomException(ErrorCode.INVALID_TOUR_SEARCH_CONDITION);
        }
        TourApiResult common = tourApiClient.getCommonDetail(contentId);
        if (common.items().isEmpty()) {
            throw new CustomException(ErrorCode.TOUR_CONTENT_NOT_FOUND);
        }
        JsonNode origin = common.items().getFirst();
        String regionCode = text(origin, "lDongRegnCd");
        String signguPart = text(origin, "lDongSignguCd");
        String title = text(origin, "title");
        if (NO_RELATED_TYPES.contains(text(origin, "contenttypeid"))
                || regionCode == null || signguPart == null || title == null) {
            return List.of();
        }

        List<JsonNode> related = findRelatedRows(title, regionCode, regionCode + signguPart);
        List<CompletableFuture<ContentSummary>> matched = related.stream()
                .filter(row -> RELATED_CATEGORY.equals(text(row, "rlteCtgryLclsNm")))
                .sorted(Comparator.comparingInt(row -> rank(row)))
                .limit(RELATED_CANDIDATES)
                .map(row -> CompletableFuture.supplyAsync(() -> matchContent(row), RELATED_MATCHER))
                .toList();

        // 순위 순서를 유지한 채 매칭된 것만 limit개까지
        return matched.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .filter(summary -> !contentId.equals(summary.contentId()))
                .limit(limit)
                .toList();
    }

    @Cacheable(cacheNames = "tourDetails", key = "#contentId")
    public ContentDetail getContentDetail(String contentId) {
        TourApiResult commonResult = tourApiClient.getCommonDetail(contentId);
        if (commonResult.items().isEmpty()) {
            throw new CustomException(ErrorCode.TOUR_CONTENT_NOT_FOUND);
        }

        JsonNode item = commonResult.items().getFirst();
        List<Image> images;
        try {
            images = tourApiClient.getImages(contentId).items().stream()
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

    /** 기준월 데이터가 아직 없을 수 있어 직전 달 → 전전달 순으로 조회한다. */
    private List<JsonNode> findRelatedRows(String title, String areaCd, String signguCd) {
        String keyword = stripParenthesis(title);
        for (int monthsAgo = 1; monthsAgo <= 2; monthsAgo++) {
            String baseYm = YearMonth.now().minusMonths(monthsAgo)
                    .format(DateTimeFormatter.ofPattern("yyyyMM"));
            List<JsonNode> rows = tourApiClient
                    .getRelatedTours(baseYm, areaCd, signguCd, keyword, RELATED_FETCH_SIZE).items();
            List<JsonNode> own = ownRows(rows, keyword);
            if (!own.isEmpty()) {
                return own;
            }
        }
        return List.of();
    }

    /** 부분 일치 검색이라 여러 중심 관광지가 섞여 오므로, 이름이 가장 가까운 한 곳의 행만 남긴다. */
    private List<JsonNode> ownRows(List<JsonNode> rows, String keyword) {
        String target = normalizeName(keyword);
        return rows.stream()
                .filter(row -> {
                    String name = normalizeName(stripParenthesis(text(row, "tAtsNm")));
                    return name.equals(target);
                })
                .toList();
    }

    private ContentSummary matchContent(JsonNode row) {
        String name = text(row, "rlteTatsNm");
        if (name == null) {
            return null;
        }
        try {
            String target = normalizeName(name);
            String signguName = text(row, "rlteSignguNm");
            return tourApiClient.searchContents(name, null, null, null, 1, 10, "A").items().stream()
                    .filter(item -> isSameName(target, text(item, "title")))
                    // 동명 장소는 연관 데이터의 시군구가 주소에 포함된 쪽을 우선
                    .min(Comparator.comparingInt(item ->
                            signguName != null && address(item) != null
                                    && address(item).contains(signguName) ? 0 : 1))
                    .map(this::toSummary)
                    .orElse(null);
        } catch (CustomException exception) {
            log.warn("related content matching failed: name={}", name);
            return null;
        }
    }

    /** 정확히 같거나 "이름 (부제)" 형태의 변형만 같은 장소로 본다. */
    private boolean isSameName(String normalizedTarget, String title) {
        if (title == null) {
            return false;
        }
        return normalizeName(stripParenthesis(title)).equals(normalizedTarget);
    }

    private int rank(JsonNode row) {
        try {
            return Integer.parseInt(row.path("rlteRank").asText());
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    private String stripParenthesis(String value) {
        return value == null ? "" : value.replaceAll("\\s*[(（\\[［【].*?[)）\\]］】]", "").trim();
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }
}
