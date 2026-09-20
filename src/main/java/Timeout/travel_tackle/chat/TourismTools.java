package Timeout.travel_tackle.chat;

import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.tour.client.TourApiClient;
import Timeout.travel_tackle.tour.dto.TourDtos.ContentSummary;
import Timeout.travel_tackle.tour.dto.TourDtos.Festival;
import Timeout.travel_tackle.tour.service.TourService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * 추천 챗봇용 TourAPI 툴. 검증된 국내 TourAPI(KorService2)를 LLM 함수 호출로 노출한다.
 * LLM이 이 메서드를 호출 → 실제 관광 데이터로 답변하므로 할루시네이션 없이 그라운딩된다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TourismTools {

    private static final int RESULT_SIZE = 8;

    private final TourService tourService;

    @Tool(description = "지역+분류로 관광지·맛집·카페·숙박·쇼핑·문화·자연·역사·레포츠를 검색한다. 실제 한국관광공사 데이터만 반환. "
            + "일반 추천은 lDongRegnCd(지역)와 category(분류)로 검색하라. 예: 서울 카페 → lDongRegnCd='11', category='카페'. "
            + "keyword는 '경복궁'처럼 특정 장소명을 콕 집어 찾을 때만 쓰고, 일반 추천엔 비워라. "
            + "반려동물·강아지·고양이·애견과 함께 갈 곳을 물으면 반드시 petFriendly=true 로 호출하라 (한국관광공사 반려동물 동반여행 데이터).")
    public String searchTourism(
            @ToolParam(required = false, description="지역의 숫자 코드만(법정동 시도, 이름 금지). 서울=11 부산=26 대구=27 인천=28 "
                    + "광주=29 대전=30 울산=31 세종=36 경기=41 강원=51 충북=43 충남=44 전북=52 전남=46 경북=47 경남=48 제주=50. 모르면 비움") String lDongRegnCd,
            @ToolParam(required = false, description="분류. 관광지/맛집/카페/숙박/쇼핑/문화시설/자연/역사/레포츠 중 하나. 모르면 비움") String category,
            @ToolParam(required = false, description="특정 장소명으로 찾을 때만(예: '경복궁'). 일반 추천엔 비움") String keyword,
            @ToolParam(required = false, description="응답 언어 코드. ko en ja zh zh-tw de fr es ru. 비우면 한국어") String language,
            @ToolParam(required = false, description="반려동물(강아지·고양이) 동반 가능 장소만 찾을 때 true. 그 외엔 비움") Boolean petFriendly
    ) {
        boolean petOnly = Boolean.TRUE.equals(petFriendly);
        // 반려동물 동반 서비스는 국문만 제공되므로 언어와 무관하게 그 서비스로 검색한다 (답변 언어는 모델이 맞춘다)
        String service = petOnly ? TourApiClient.PET_SERVICE : toService(language);
        String region = regionDigits(lDongRegnCd);
        Category cat = toCategory(category);
        try {
            List<ContentSummary> items;
            if (cat != null || region != null) {
                // 지역/분류 기반 (areaBasedList2). contentTypeId는 언어별로 코드가 달라 쓰지 않고,
                // 모든 언어 공통인 lclsSystm(분류체계)으로만 필터한다.
                items = tourService.getFilteredContentsInLanguage(
                        service, region, null,
                        cat == null ? null : cat.lclsSystm1(),
                        cat == null ? null : cat.lclsSystm2(),
                        RESULT_SIZE);
            } else if (StringUtils.hasText(keyword)) {
                items = tourService.searchInLanguage(service, keyword, null, RESULT_SIZE);
            } else {
                items = tourService.getFilteredContentsInLanguage(service, null, null, null, null, RESULT_SIZE);
            }
            log.info("[TOOL] searchTourism region={} category={} keyword={} lang={} pet={} -> {} results",
                    lDongRegnCd, category, keyword, language, petOnly, items.size());
            String label = StringUtils.hasText(category) ? category
                    : (StringUtils.hasText(keyword) ? keyword : "추천");
            String result = formatContents(petOnly ? "반려동물 동반 가능 " + label : label, items);
            return petOnly && !items.isEmpty()
                    ? result + "\n(위 장소는 모두 한국관광공사 반려동물 동반여행 서비스에 등록된, 반려동물과 함께 갈 수 있는 곳입니다)"
                    : result;
        } catch (CustomException e) {
            log.warn("searchTourism failed: region={} category={} lang={} error={}",
                    lDongRegnCd, category, language, e.getMessage());
            return "관광 정보를 불러오지 못했습니다. 잠시 후 다시 시도하세요.";
        }
    }

    @Tool(description = "특정 지역의 이번 달(진행 중 + 다가오는) 축제·행사를 검색한다. 실제 한국관광공사 데이터만 반환한다.")
    public String searchFestivals(
            @ToolParam(required = false, description="지역의 숫자 코드만 넣어라(법정동 시도 코드, 이름 금지). "
                    + "서울=11 부산=26 대구=27 인천=28 광주=29 대전=30 울산=31 세종=36 경기=41 강원=51 "
                    + "충북=43 충남=44 전북=52 전남=46 경북=47 경남=48 제주=50. 예: 서울이면 '11'. 모르면 비움") String lDongRegnCd,
            @ToolParam(required = false, description="응답 언어 코드. ko en ja zh zh-tw de fr es ru. 비우면 한국어") String language
    ) {
        try {
            LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
            List<Festival> festivals = tourService.getFestivalsInLanguage(
                    toService(language), startOfMonth, null, regionDigits(lDongRegnCd), RESULT_SIZE
            );
            log.info("[TOOL] searchFestivals region={} lang={} -> {} festivals",
                    lDongRegnCd, language, festivals.size());
            return formatFestivals(festivals);
        } catch (CustomException e) {
            log.warn("searchFestivals failed: region={}, lang={}, error={}", lDongRegnCd, language, e.getMessage());
            return "축제 정보를 불러오지 못했습니다. 잠시 후 다시 시도하세요.";
        }
    }

    // --- 내부 헬퍼 ---

    /** 대화 언어 코드 → TourAPI 언어별 서비스. 한국어가 기본. */
    private String toService(String language) {
        if (!StringUtils.hasText(language)) {
            return "KorService2";
        }
        return switch (language.trim().toLowerCase()) {
            case "ko", "kr", "ko-kr", "kor" -> "KorService2";
            case "en", "eng", "english" -> "EngService2";
            case "ja", "jp", "jpn" -> "JpnService2";
            case "zh", "zh-cn", "zh-hans", "chs" -> "ChsService2";
            case "zh-tw", "zh-hant", "cht" -> "ChtService2";
            case "de", "ger" -> "GerService2";
            case "fr", "fre" -> "FreService2";
            case "es", "spn" -> "SpnService2";
            case "ru", "rus" -> "RusService2";
            default -> "KorService2";
        };
    }

    /** 분류 → lclsSystm(분류체계) 코드. 모든 언어 서비스 공통이라 contentTypeId 대신 이걸로 필터한다. */
    private record Category(String lclsSystm1, String lclsSystm2) {
    }

    private Category toCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return null;
        }
        return switch (category.trim()) {
            case "관광지" -> new Category(null, null);   // 분류 없이 지역 전체
            case "맛집", "음식점" -> new Category("FD", null);
            case "카페" -> new Category("FD", "FD05");
            case "숙박", "호텔" -> new Category("AC", null);
            case "쇼핑" -> new Category("SH", null);
            case "문화시설", "문화" -> new Category("VE", null);
            case "자연" -> new Category("NA", null);
            case "역사" -> new Category("HS", null);
            case "레포츠", "액티비티" -> new Category("LS", null);
            default -> null;
        };
    }

    private String formatContents(String keyword, List<ContentSummary> items) {
        if (items.isEmpty()) {
            return "'" + keyword + "'에 대한 검색 결과가 없습니다.";
        }
        StringBuilder sb = new StringBuilder("검색 결과:");
        int index = 1;
        for (ContentSummary item : items) {
            sb.append("\n").append(index++).append(". ").append(item.title());
            if (StringUtils.hasText(item.address())) {
                sb.append(" | ").append(item.address());
            }
            if (StringUtils.hasText(item.telephone())) {
                sb.append(" | ☎ ").append(item.telephone());
            }
        }
        return sb.toString();
    }

    private String formatFestivals(List<Festival> festivals) {
        if (festivals.isEmpty()) {
            return "해당 조건의 축제·행사가 없습니다.";
        }
        StringBuilder sb = new StringBuilder("축제·행사:");
        int index = 1;
        for (Festival festival : festivals) {
            sb.append("\n").append(index++).append(". ").append(festival.title());
            if (StringUtils.hasText(festival.address())) {
                sb.append(" | ").append(festival.address());
            }
            if (festival.startDate() != null) {
                sb.append(" | ").append(festival.startDate());
                if (festival.endDate() != null) {

                    sb.append(" ~ ").append(festival.endDate());
                }
            }
        }
        return sb.toString();
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** LLM이 "서울11"처럼 이름+코드를 붙여 보내도 숫자 코드만 뽑아낸다(없으면 null → 전국 검색). */
    private String regionDigits(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }
}
