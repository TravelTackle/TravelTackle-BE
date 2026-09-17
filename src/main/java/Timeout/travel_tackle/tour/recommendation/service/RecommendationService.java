package Timeout.travel_tackle.tour.recommendation.service;
import Timeout.travel_tackle.tour.recommendation.PreferenceMapper;

import Timeout.travel_tackle.entity.Enum.InterestTag;
import Timeout.travel_tackle.entity.Enum.PreferredRegion;
import Timeout.travel_tackle.entity.UserPreference;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.util.UuidConverter;
import Timeout.travel_tackle.preference.repository.UserPreferenceRepository;
import Timeout.travel_tackle.tour.dto.RecommendationDtos.RecommendedSection;
import Timeout.travel_tackle.tour.dto.RecommendationDtos.RecommendationsResponse;
import Timeout.travel_tackle.tour.dto.TourDtos.ContentSummary;
import Timeout.travel_tackle.tour.dto.TourDtos.Festival;
import Timeout.travel_tackle.tour.service.TourService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private static final int FETCH_SIZE = 20;
    private static final int SECTION_SIZE = 10;
    private static final int MIN_RESULTS = 5;

    private static final Set<InterestTag> DEDICATED_SECTION_TAGS =
            Set.of(InterestTag.FOOD, InterestTag.CAFE, InterestTag.FESTIVAL);

    private final TourService tourService;
    private final UserPreferenceRepository userPreferenceRepository;

    public RecommendationsResponse getRecommendations(String subject) {
        UUID userId = UuidConverter.fromSubject(subject);
        Optional<UserPreference> preferenceOpt = userPreferenceRepository.findByUserId(userId);

        if (preferenceOpt.isEmpty()) {
            return buildDefaultRecommendations();
        }

        UserPreference preference = preferenceOpt.get();
        String lDongRegnCd = pickRandomLDongRegnCd(preference.getPreferredRegions());
        String regionName = toRegionName(preference.getPreferredRegions(), lDongRegnCd);
        Set<String> preferredAreaCodes = preference.getPreferredRegions().stream()
                .map(PreferenceMapper::toAreaCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return new RecommendationsResponse(List.of(
                buildPersonalSection(preference.getInterestTags(), lDongRegnCd, preferredAreaCodes),
                buildFoodSection(lDongRegnCd, regionName),
                buildCafeSection(lDongRegnCd, regionName),
                buildFestivalSection(lDongRegnCd)
        ));
    }

    private RecommendedSection buildPersonalSection(Set<InterestTag> tags, String lDongRegnCd,
                                                    Set<String> preferredAreaCodes) {
        List<InterestTag> candidates = tags.stream()
                .filter(t -> !DEDICATED_SECTION_TAGS.contains(t))
                .sorted()
                .toList();

        if (candidates.isEmpty()) {
            candidates = List.of(InterestTag.NATURE, InterestTag.HISTORY);
        }

        // 관심사별 후보를 TourAPI 관련도 순서 그대로 보관한다(관심사 전부 반영).
        List<List<ContentSummary>> perTag = candidates.stream()
                .map(tag -> fetchFiltered(lDongRegnCd, PreferenceMapper.toApiParams(tag)))
                .toList();

        // 라운드로빈으로 합쳐 모든 관심사를 고르게 반영하고,
        // 선호 지역과 일치하는 항목을 앞으로 보낸다(안정 정렬이라 관련도 순서는 유지).
        List<ContentSummary> ordered = roundRobin(perTag).stream()
                .sorted(Comparator.comparingInt(
                        c -> preferredAreaCodes.contains(c.areaCode()) ? 0 : 1))
                .toList();

        return new RecommendedSection("personal", "맞춤 추천", takeTop(ordered, SECTION_SIZE));
    }

    /** 여러 후보 목록을 한 개씩 번갈아 뽑아 합친다(contentId 기준 중복 제거). */
    private List<ContentSummary> roundRobin(List<List<ContentSummary>> lists) {
        List<ContentSummary> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int max = lists.stream().mapToInt(List::size).max().orElse(0);
        for (int i = 0; i < max; i++) {
            for (List<ContentSummary> list : lists) {
                if (i < list.size() && seen.add(list.get(i).contentId())) {
                    result.add(list.get(i));
                }
            }
        }
        return result;
    }

    private List<ContentSummary> takeTop(List<ContentSummary> items, int size) {
        return items.subList(0, Math.min(size, items.size()));
    }

    private RecommendedSection buildFoodSection(String lDongRegnCd, String regionName) {
        List<ContentSummary> items = fetchFiltered(lDongRegnCd,
                new PreferenceMapper.TourApiParams("39", "FD", null));
        String title = regionName.isBlank() ? "맛집" : regionName + " 맛집";
        return new RecommendedSection("food", title, shuffleAndTake(items));
    }

    private RecommendedSection buildCafeSection(String lDongRegnCd, String regionName) {
        List<ContentSummary> items = fetchFiltered(lDongRegnCd,
                new PreferenceMapper.TourApiParams("39", "FD", "FD05"));
        String title = regionName.isBlank() ? "카페" : regionName + " 카페";
        return new RecommendedSection("cafe", title, shuffleAndTake(items));
    }

    private RecommendedSection buildFestivalSection(String lDongRegnCd) {
        LocalDate today = LocalDate.now();
        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());
        try {
            List<ContentSummary> items = tourService.getFestivals(today, endOfMonth, lDongRegnCd, 1, FETCH_SIZE)
                    .items().stream()
                    .map(this::festivalToSummary)
                    .collect(Collectors.toCollection(ArrayList::new));
            Collections.shuffle(items);
            return new RecommendedSection("festival", "이달의 축제",
                    items.subList(0, Math.min(SECTION_SIZE, items.size())));
        } catch (CustomException e) {
            log.warn("Festival fetch failed for recommendation: {}", e.getMessage());
            return new RecommendedSection("festival", "이달의 축제", List.of());
        }
    }

    private List<ContentSummary> fetchFiltered(String lDongRegnCd, PreferenceMapper.TourApiParams params) {
        try {
            List<ContentSummary> items = tourService.getFilteredContents(
                    lDongRegnCd, params.contentTypeId(), params.lclsSystm1(), params.lclsSystm2(), FETCH_SIZE);

            if (items.size() < MIN_RESULTS && lDongRegnCd != null) {
                items = tourService.getFilteredContents(
                        null, params.contentTypeId(), params.lclsSystm1(), params.lclsSystm2(), FETCH_SIZE);
            }
            return items;
        } catch (CustomException e) {
            log.warn("Filtered content fetch failed for recommendation: {}", e.getMessage());
            return List.of();
        }
    }

    private List<ContentSummary> shuffleAndTake(List<ContentSummary> items) {
        List<ContentSummary> mutable = new ArrayList<>(items);
        Collections.shuffle(mutable);
        return mutable.subList(0, Math.min(SECTION_SIZE, mutable.size()));
    }

    private RecommendationsResponse buildDefaultRecommendations() {
        try {
            List<ContentSummary> items = tourService.getFilteredContents(
                    null, "12", null, null, FETCH_SIZE);
            return new RecommendationsResponse(List.of(
                    new RecommendedSection("default", "이번 주 인기 여행지", shuffleAndTake(items))));
        } catch (CustomException e) {
            return new RecommendationsResponse(List.of());
        }
    }

    private String pickRandomLDongRegnCd(Set<PreferredRegion> regions) {
        List<String> codes = regions.stream()
                .map(PreferenceMapper::toLDongRegnCd)
                .filter(Objects::nonNull)
                .toList();
        return codes.isEmpty() ? null : codes.get(new Random().nextInt(codes.size()));
    }

    private String toRegionName(Set<PreferredRegion> regions, String selectedLDongRegnCd) {
        if (selectedLDongRegnCd == null) return "";
        return regions.stream()
                .filter(r -> selectedLDongRegnCd.equals(PreferenceMapper.toLDongRegnCd(r)))
                .findFirst()
                .map(this::regionDisplayName)
                .orElse("");
    }

    private String regionDisplayName(PreferredRegion region) {
        return switch (region) {
            case SEOUL       -> "서울";
            case INCHEON     -> "인천";
            case BUSAN       -> "부산";
            case GANGWON     -> "강원";
            case CHUNGCHEONG -> "충청";
            case GYEONGBUK   -> "경북";
            case GYEONGJU    -> "경주";
            case JEONJU      -> "전주";
            case JEONNAM     -> "전남";
            case JEJU        -> "제주";
            case OTHER       -> "";
        };
    }

    private ContentSummary festivalToSummary(Festival festival) {
        return new ContentSummary(
                festival.contentId(),
                "15",
                festival.title(),
                festival.address(),
                festival.areaCode(),
                null,
                null, null, null,
                festival.imageUrl(),
                festival.longitude(),
                festival.latitude(),
                null
        );
    }
}
