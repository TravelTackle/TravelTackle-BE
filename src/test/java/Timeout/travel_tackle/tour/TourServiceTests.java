package Timeout.travel_tackle.tour;

import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import Timeout.travel_tackle.tour.client.TourApiClient;
import Timeout.travel_tackle.tour.client.TourApiClient.TourApiResult;
import Timeout.travel_tackle.tour.service.TourService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TourServiceTests {

    @Mock TourApiClient tourApiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TourService tourService;

    @BeforeEach
    void setUp() {
        tourService = new TourService(tourApiClient);
    }

    @Test
    void mapsAreaContentResponseToOurDto() throws Exception {
        JsonNode item = objectMapper.readTree("""
                {
                  "contentid": "125266",
                  "contenttypeid": "12",
                  "title": "경복궁",
                  "addr1": "서울특별시 종로구",
                  "addr2": "사직로 161",
                  "areacode": "1",
                  "sigungucode": "23",
                  "firstimage": "https://example.com/image.jpg",
                  "mapx": "126.976993",
                  "mapy": "37.578822"
                }
                """);
        when(tourApiClient.getAreaContents(TourApiClient.DEFAULT_SERVICE, "1", "23", "12", 1, 20, "A"))
                .thenReturn(new TourApiResult(List.of(item), 1, 20, 1));

        var result = tourService.getContents(null, "1", "23", "12", 1, 20, "A");

        assertEquals(1, result.totalCount());
        assertEquals("경복궁", result.items().getFirst().title());
        assertEquals("서울특별시 종로구 사직로 161", result.items().getFirst().address());
        assertEquals(126.976993, result.items().getFirst().longitude());
    }

    @Test
    void keywordUsesKeywordSearchEndpoint() {
        when(tourApiClient.searchContents(TourApiClient.DEFAULT_SERVICE, "한류", null, null, null, 1, 10, "A"))
                .thenReturn(new TourApiResult(List.of(), 1, 10, 0));

        tourService.getContents(" 한류 ", null, null, null, 1, 10, "A");

        verify(tourApiClient).searchContents(TourApiClient.DEFAULT_SERVICE, "한류", null, null, null, 1, 10, "A");
        verify(tourApiClient, never()).getAreaContents(any(), any(), any(), any(), anyInt(), anyInt(), any());
    }

    @Test
    void petFriendlySearchUsesPetTourServiceWithSameFilters() {
        when(tourApiClient.searchContents(TourApiClient.PET_SERVICE, "공원", "1", null, "12", 1, 10, "A"))
                .thenReturn(new TourApiResult(List.of(), 1, 10, 0));
        when(tourApiClient.getAreaContents(TourApiClient.PET_SERVICE, "6", "16", null, 1, 20, "A"))
                .thenReturn(new TourApiResult(List.of(), 1, 20, 0));
        when(tourApiClient.getNearbyContents(TourApiClient.PET_SERVICE, 129.16, 35.15, 3000, null, 1, 5))
                .thenReturn(new TourApiResult(List.of(), 1, 5, 0));

        tourService.getContents("공원", "1", null, "12", 1, 10, "A", true);
        tourService.getContents(null, "6", "16", null, 1, 20, "A", true);
        tourService.getNearbyContents(129.16, 35.15, 3000, null, 1, 5, true);

        verify(tourApiClient).searchContents(TourApiClient.PET_SERVICE, "공원", "1", null, "12", 1, 10, "A");
        verify(tourApiClient).getAreaContents(TourApiClient.PET_SERVICE, "6", "16", null, 1, 20, "A");
        verify(tourApiClient).getNearbyContents(TourApiClient.PET_SERVICE, 129.16, 35.15, 3000, null, 1, 5);
        verify(tourApiClient, never()).searchContents(eq(TourApiClient.DEFAULT_SERVICE), any(), any(), any(), any(), anyInt(), anyInt(), any());
    }

    @Test
    void detailCarriesPetInfoWhenRegisteredAndSurvivesPetServiceOutage() throws Exception {
        JsonNode detail = objectMapper.readTree("""
                {"contentid":"129501","title":"낙산공원"}
                """);
        JsonNode pet = objectMapper.readTree("""
                {"contentid":"129501","acmpyTypeCd":"전구역 동반가능","acmpyPsblCpam":"전 견종 동반 가능",
                 "acmpyNeedMtr":"목줄 착용","etcAcmpyInfo":"입마개 착용 필수","relaPosesFclty":""}
                """);
        when(tourApiClient.getCommonDetail("129501")).thenReturn(new TourApiResult(List.of(detail), 1, 1, 1));
        when(tourApiClient.getImages("129501")).thenReturn(new TourApiResult(List.of(), 1, 30, 0));
        when(tourApiClient.getPetDetail("129501")).thenReturn(new TourApiResult(List.of(pet), 1, 10, 1));

        var withPet = tourService.getContentDetail("129501");
        assertEquals("전구역 동반가능", withPet.petInfo().companionType());
        assertEquals("전 견종 동반 가능", withPet.petInfo().allowedAnimals());
        assertEquals("목줄 착용", withPet.petInfo().requirements());
        assertEquals("입마개 착용 필수", withPet.petInfo().notes());
        assertNull(withPet.petInfo().facilities()); // 빈 문자열은 null 로

        // 반려동물 서비스 장애 시에도 상세 조회는 성공한다
        when(tourApiClient.getCommonDetail("777")).thenReturn(new TourApiResult(List.of(detail), 1, 1, 1));
        when(tourApiClient.getImages("777")).thenReturn(new TourApiResult(List.of(), 1, 30, 0));
        when(tourApiClient.getPetDetail("777")).thenThrow(new CustomException(ErrorCode.TOUR_API_UNAVAILABLE));
        assertNull(tourService.getContentDetail("777").petInfo());
    }

    @Test
    void rejectsInvalidNearbyRadiusBeforeCallingExternalApi() {
        assertThrows(CustomException.class, () ->
                tourService.getNearbyContents(127.0, 37.5, 20_001, null, 1, 20));
        verifyNoInteractions(tourApiClient);
    }

    @Test
    void combinesCommonDetailAndImages() throws Exception {
        JsonNode detail = objectMapper.readTree("""
                {"contentid":"125266","title":"경복궁","overview":"궁궐 설명"}
                """);
        JsonNode image = objectMapper.readTree("""
                {"originimgurl":"https://example.com/original.jpg","cpyrhtDivCd":"Type1"}
                """);
        when(tourApiClient.getCommonDetail("125266"))
                .thenReturn(new TourApiResult(List.of(detail), 1, 1, 1));
        when(tourApiClient.getImages("125266"))
                .thenReturn(new TourApiResult(List.of(image), 1, 30, 1));
        when(tourApiClient.getPetDetail("125266"))
                .thenReturn(new TourApiResult(List.of(), 1, 10, 0)); // 반려동물 서비스 미등록

        var result = tourService.getContentDetail("125266");

        assertEquals("경복궁", result.title());
        assertEquals("궁궐 설명", result.overview());
        assertEquals("https://example.com/original.jpg", result.images().getFirst().originalUrl());
        assertNull(result.petInfo());
    }

    @Test
    void relatedContentsFollowRankAndSkipUnmatchedOrNonAttractions() throws Exception {
        when(tourApiClient.getCommonDetail("1")).thenReturn(single("""
                {"contentid":"1","contenttypeid":"12","title":"성산일출봉",
                 "lDongRegnCd":"50","lDongSignguCd":"130"}"""));
        // 순위가 뒤섞여 내려와도 rlteRank 순으로 정렬되어야 한다
        when(tourApiClient.getRelatedTours(anyString(), eq("50"), eq("50130"), eq("성산일출봉"), anyInt()))
                .thenReturn(new TourApiResult(List.of(
                        related("성산일출봉", "비자림", "관광지", "3"),
                        related("성산일출봉", "섭지코지", "관광지", "1"),
                        related("성산일출봉", "아쿠아플라넷", "관광지", "2"),     // TourAPI 미등록
                        related("성산일출봉", "제주 흑돼지집", "음식", "4"),       // 관광지가 아님
                        related("성산 다른곳", "엉뚱한곳", "관광지", "5")          // 다른 중심 관광지
                ), 1, 50, 5));
        stubSearch("섭지코지", "127813", "섭지코지");
        stubSearch("비자림", "126472", "비자림");
        when(tourApiClient.searchContents(eq("아쿠아플라넷"), any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(new TourApiResult(List.of(), 1, 10, 0));

        var result = tourService.getRelatedContents("1", 8);

        assertEquals(List.of("127813", "126472"),
                result.stream().map(c -> c.contentId()).toList());
    }

    @Test
    void relatedContentsAcceptsParentheticalTitleVariantAndHonorsLimit() throws Exception {
        when(tourApiClient.getCommonDetail("1")).thenReturn(single("""
                {"contentid":"1","contenttypeid":"12","title":"성산일출봉",
                 "lDongRegnCd":"50","lDongSignguCd":"130"}"""));
        when(tourApiClient.getRelatedTours(anyString(), any(), any(), any(), anyInt()))
                .thenReturn(new TourApiResult(List.of(
                        related("성산일출봉", "함덕해수욕장", "관광지", "1"),
                        related("성산일출봉", "섭지코지", "관광지", "2")), 1, 50, 2));
        stubSearch("함덕해수욕장", "10", "함덕해수욕장 (함덕 서우봉 해변)");
        stubSearch("섭지코지", "11", "섭지코지");

        var result = tourService.getRelatedContents("1", 1);

        assertEquals(1, result.size());
        assertEquals("10", result.getFirst().contentId());
    }

    @Test
    void relatedContentsIgnoreSquareBracketSubtitleInOriginTitle() throws Exception {
        when(tourApiClient.getCommonDetail("1")).thenReturn(single("""
                {"contentid":"1","contenttypeid":"12","title":"성산일출봉 [유네스코 세계자연유산]",
                 "lDongRegnCd":"50","lDongSignguCd":"130"}"""));
        when(tourApiClient.getRelatedTours(anyString(), any(), any(), eq("성산일출봉"), anyInt()))
                .thenReturn(new TourApiResult(List.of(
                        related("성산일출봉", "섭지코지", "관광지", "1")), 1, 50, 1));
        stubSearch("섭지코지", "127813", "섭지코지");

        assertEquals(1, tourService.getRelatedContents("1", 8).size());
    }

    @Test
    void relatedContentsAreEmptyForRestaurantsWithoutCallingRelatedApi() throws Exception {
        when(tourApiClient.getCommonDetail("9")).thenReturn(single("""
                {"contentid":"9","contenttypeid":"39","title":"식당",
                 "lDongRegnCd":"50","lDongSignguCd":"130"}"""));

        assertTrue(tourService.getRelatedContents("9", 8).isEmpty());
        verify(tourApiClient, never()).getRelatedTours(any(), any(), any(), any(), anyInt());
    }

    @Test
    void relatedContentsFallBackToPreviousMonthWhenLatestMonthHasNoData() throws Exception {
        when(tourApiClient.getCommonDetail("1")).thenReturn(single("""
                {"contentid":"1","contenttypeid":"12","title":"성산일출봉",
                 "lDongRegnCd":"50","lDongSignguCd":"130"}"""));
        when(tourApiClient.getRelatedTours(anyString(), any(), any(), any(), anyInt()))
                .thenReturn(new TourApiResult(List.of(), 1, 50, 0))
                .thenReturn(new TourApiResult(List.of(
                        related("성산일출봉", "섭지코지", "관광지", "1")), 1, 50, 1));
        stubSearch("섭지코지", "127813", "섭지코지");

        assertEquals(1, tourService.getRelatedContents("1", 8).size());
        verify(tourApiClient, times(2)).getRelatedTours(anyString(), any(), any(), any(), anyInt());
    }

    @Test
    void relatedContentsRejectOutOfRangeLimit() {
        assertThrows(CustomException.class, () -> tourService.getRelatedContents("1", 9));
        assertThrows(CustomException.class, () -> tourService.getRelatedContents("1", 0));
    }

    private TourApiResult single(String json) throws Exception {
        return new TourApiResult(List.of(objectMapper.readTree(json)), 1, 1, 1);
    }

    private JsonNode related(String center, String name, String category, String rank) throws Exception {
        return objectMapper.readTree("""
                {"tAtsNm":"%s","rlteTatsNm":"%s","rlteCtgryLclsNm":"%s","rlteRank":"%s",
                 "rlteSignguNm":"서귀포시"}""".formatted(center, name, category, rank));
    }

    private void stubSearch(String keyword, String contentId, String title) throws Exception {
        JsonNode item = objectMapper.readTree("""
                {"contentid":"%s","contenttypeid":"12","title":"%s","addr1":"제주특별자치도 서귀포시"}"""
                .formatted(contentId, title));
        when(tourApiClient.searchContents(eq(keyword), any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(new TourApiResult(List.of(item), 1, 10, 1));
    }
}
