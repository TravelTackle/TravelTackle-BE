package Timeout.travel_tackle.tour;

import Timeout.travel_tackle.global.exception.CustomException;
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
        when(tourApiClient.getAreaContents("1", "23", "12", 1, 20, "A"))
                .thenReturn(new TourApiResult(List.of(item), 1, 20, 1));

        var result = tourService.getContents(null, "1", "23", "12", 1, 20, "A");

        assertEquals(1, result.totalCount());
        assertEquals("경복궁", result.items().getFirst().title());
        assertEquals("서울특별시 종로구 사직로 161", result.items().getFirst().address());
        assertEquals(126.976993, result.items().getFirst().longitude());
    }

    @Test
    void keywordUsesKeywordSearchEndpoint() {
        when(tourApiClient.searchContents("한류", null, null, null, 1, 10, "A"))
                .thenReturn(new TourApiResult(List.of(), 1, 10, 0));

        tourService.getContents(" 한류 ", null, null, null, 1, 10, "A");

        verify(tourApiClient).searchContents("한류", null, null, null, 1, 10, "A");
        verify(tourApiClient, never()).getAreaContents(any(), any(), any(), anyInt(), anyInt(), any());
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

        var result = tourService.getContentDetail("125266");

        assertEquals("경복궁", result.title());
        assertEquals("궁궐 설명", result.overview());
        assertEquals("https://example.com/original.jpg", result.images().getFirst().originalUrl());
    }
}
