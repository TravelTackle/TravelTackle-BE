package Timeout.travel_tackle.tour.client;

import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Component
@Slf4j
public class TourApiClient {

    private static final String SUCCESS_CODE = "0000";
    private static final String DEFAULT_SERVICE = "KorService2"; // 언어 미지정 시 국문 서비스

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String serviceKey;
    private final String mobileApp;

    public TourApiClient(
            @Value("${tour.base-url:https://apis.data.go.kr/B551011}") String baseUrl,
            @Value("${tour.service-key:}") String serviceKey,
            @Value("${tour.mobile-app:TravelTackle}") String mobileApp
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = new ObjectMapper();
        this.serviceKey = serviceKey;
        this.mobileApp = mobileApp;
    }

    public TourApiResult getAreas(String areaCode) {
        return request("areaCode2", builder -> {
            addIfPresent(builder, "areaCode", areaCode);
            builder.queryParam("pageNo", 1).queryParam("numOfRows", 100);
        });
    }

    public TourApiResult getCategories(
            String contentTypeId,
            String category1,
            String category2,
            String category3
    ) {
        return request("categoryCode2", builder -> {
            addIfPresent(builder, "contentTypeId", contentTypeId);
            addIfPresent(builder, "cat1", category1);
            addIfPresent(builder, "cat2", category2);
            addIfPresent(builder, "cat3", category3);
        });
    }

    public TourApiResult getAreaContents(
            String areaCode,
            String sigunguCode,
            String contentTypeId,
            int page,
            int size,
            String arrange
    ) {
        return request("areaBasedList2", builder -> {
            addIfPresent(builder, "areaCode", areaCode);
            addIfPresent(builder, "sigunguCode", sigunguCode);
            addIfPresent(builder, "contentTypeId", contentTypeId);
            builder.queryParam("pageNo", page)
                    .queryParam("numOfRows", size)
                    .queryParam("arrange", arrange);
        });
    }

    public TourApiResult searchContents(
            String keyword,
            String areaCode,
            String sigunguCode,
            String contentTypeId,
            int page,
            int size,
            String arrange
    ) {
        return searchContents(DEFAULT_SERVICE, keyword, areaCode, sigunguCode,
                contentTypeId, page, size, arrange);
    }

    /** 언어별 서비스(KorService2/EngService2/JpnService2/...)로 키워드 검색. */
    public TourApiResult searchContents(
            String service,
            String keyword,
            String areaCode,
            String sigunguCode,
            String contentTypeId,
            int page,
            int size,
            String arrange
    ) {
        return request(service, "searchKeyword2", builder -> {
            builder.queryParam("keyword", keyword)
                    .queryParam("pageNo", page)
                    .queryParam("numOfRows", size)
                    .queryParam("arrange", arrange);
            addIfPresent(builder, "areaCode", areaCode);
            addIfPresent(builder, "sigunguCode", sigunguCode);
            addIfPresent(builder, "contentTypeId", contentTypeId);
        });
    }

    public TourApiResult getNearbyContents(
            double longitude,
            double latitude,
            int radius,
            String contentTypeId,
            int page,
            int size
    ) {
        return request("locationBasedList2", builder -> {
            builder.queryParam("mapX", longitude)
                    .queryParam("mapY", latitude)
                    .queryParam("radius", radius)
                    .queryParam("pageNo", page)
                    .queryParam("numOfRows", size)
                    .queryParam("arrange", "E");
            addIfPresent(builder, "contentTypeId", contentTypeId);
        });
    }

    public TourApiResult getCommonDetail(String contentId) {
        return request("detailCommon2", builder -> builder.queryParam("contentId", contentId));
    }

    public TourApiResult getImages(String contentId) {
        return request("detailImage2", builder -> builder
                .queryParam("contentId", contentId)
                .queryParam("imageYN", "Y")
                .queryParam("subImageYN", "Y")
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 30));
    }

    public TourApiResult getFestivals(
            String startDate,
            String endDate,
            String lDongRegnCd,
            int page,
            int size
    ) {
        return getFestivals(DEFAULT_SERVICE, startDate, endDate, lDongRegnCd, page, size);
    }

    /**
     * 언어별 서비스로 축제 검색. v2 축제 데이터는 areaCode가 비어 있으므로
     * 지역 필터는 lDongRegnCd(법정동 시도 코드)로 한다.
     */
    public TourApiResult getFestivals(
            String service,
            String startDate,
            String endDate,
            String lDongRegnCd,
            int page,
            int size
    ) {
        return request(service, "searchFestival2", builder -> {
            builder.queryParam("eventStartDate", startDate)
                    .queryParam("pageNo", page)
                    .queryParam("numOfRows", size)
                    .queryParam("arrange", "A");
            addIfPresent(builder, "eventEndDate", endDate);
            addIfPresent(builder, "lDongRegnCd", lDongRegnCd);
        });
    }

    public TourApiResult getFilteredContents(
            String lDongRegnCd,
            String contentTypeId,
            String lclsSystm1,
            String lclsSystm2,
            int page,
            int size
    ) {
        return getFilteredContents(DEFAULT_SERVICE, lDongRegnCd, contentTypeId,
                lclsSystm1, lclsSystm2, page, size);
    }

    /** 언어별 서비스로 지역+분류(areaBasedList2) 검색. 키워드가 없어도 지역/유형으로 그라운딩된다. */
    public TourApiResult getFilteredContents(
            String service,
            String lDongRegnCd,
            String contentTypeId,
            String lclsSystm1,
            String lclsSystm2,
            int page,
            int size
    ) {
        return request(service, "areaBasedList2", builder -> {
            addIfPresent(builder, "lDongRegnCd", lDongRegnCd);
            addIfPresent(builder, "contentTypeId", contentTypeId);
            addIfPresent(builder, "lclsSystm1", lclsSystm1);
            addIfPresent(builder, "lclsSystm2", lclsSystm2);
            builder.queryParam("pageNo", page)
                    .queryParam("numOfRows", size)
                    .queryParam("arrange", "A");
        });
    }

    public TourApiResult getStays(
            String areaCode,
            String sigunguCode,
            int page,
            int size
    ) {
        return request("searchStay2", builder -> {
            builder.queryParam("pageNo", page)
                    .queryParam("numOfRows", size)
                    .queryParam("arrange", "A");
            addIfPresent(builder, "areaCode", areaCode);
            addIfPresent(builder, "sigunguCode", sigunguCode);
        });
    }

    private TourApiResult request(String endpoint, Consumer<UriBuilder> query) {
        return request(DEFAULT_SERVICE, endpoint, query);
    }

    private TourApiResult request(String service, String endpoint, Consumer<UriBuilder> query) {
        if (!StringUtils.hasText(serviceKey)) {
            throw new CustomException(ErrorCode.TOUR_API_NOT_CONFIGURED);
        }

        try {
            String payload = restClient.get()
                    .uri(builder -> {
                        builder.path("/" + service + "/" + endpoint)
                                .queryParam("serviceKey", serviceKey)
                                .queryParam("MobileOS", "WEB")
                                .queryParam("MobileApp", mobileApp)
                                .queryParam("_type", "json");
                        query.accept(builder);
                        return builder.build();
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new CustomException(ErrorCode.TOUR_API_UNAVAILABLE);
                    })
                    .body(String.class);

            return parse(endpoint, payload);
        } catch (CustomException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("Tour API request failed: endpoint={}, type={}",
                    endpoint, exception.getClass().getSimpleName());
            throw new CustomException(ErrorCode.TOUR_API_UNAVAILABLE);
        }
    }

    private TourApiResult parse(String endpoint, String payload) {
        if (!StringUtils.hasText(payload)) {
            log.warn("Tour API returned an empty response: endpoint={}", endpoint);
            throw new CustomException(ErrorCode.TOUR_API_UNAVAILABLE);
        }

        JsonNode response;
        try {
            response = objectMapper.readTree(payload).path("response");
        } catch (JsonProcessingException exception) {
            log.warn("Tour API returned a non-JSON response: endpoint={}, body={}",
                    endpoint, summarize(payload));
            throw new CustomException(ErrorCode.TOUR_API_UNAVAILABLE);
        }

        String resultCode = response.path("header").path("resultCode").asText();
        if (!SUCCESS_CODE.equals(resultCode)) {
            String resultMessage = response.path("header").path("resultMsg").asText("UNKNOWN");
            log.warn("Tour API rejected request: endpoint={}, resultCode={}, resultMessage={}",
                    endpoint, resultCode, resultMessage);
            throw new CustomException(ErrorCode.TOUR_API_UNAVAILABLE);
        }

        JsonNode body = response.path("body");
        JsonNode itemNode = body.path("items").path("item");
        List<JsonNode> items = new ArrayList<>();
        if (itemNode.isArray()) {
            itemNode.forEach(items::add);
        } else if (itemNode.isObject()) {
            items.add(itemNode);
        }

        return new TourApiResult(
                List.copyOf(items),
                body.path("pageNo").asInt(1),
                body.path("numOfRows").asInt(items.size()),
                body.path("totalCount").asInt(items.size())
        );
    }

    private void addIfPresent(UriBuilder builder, String name, String value) {
        if (StringUtils.hasText(value)) {
            builder.queryParam(name, value);
        }
    }

    private String summarize(String payload) {
        String singleLine = payload.replaceAll("\\s+", " ").trim();
        return singleLine.substring(0, Math.min(singleLine.length(), 300));
    }

    public record TourApiResult(List<JsonNode> items, int page, int size, int totalCount) {
    }
}
