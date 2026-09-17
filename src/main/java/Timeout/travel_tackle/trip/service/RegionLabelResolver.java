package Timeout.travel_tackle.trip.service;

import Timeout.travel_tackle.trip.dto.TripDetailResponse;

/**
 * 캐싱된 주소 문자열에서 짧은 지역 라벨을 뽑아낸다 (예: "경기도 수원시 팔달구..." -> "수원").
 * 첫 토큰이 광역시급(특별시/광역시/특별자치시)이면 그 자체가 지역명이고,
 * 도(道) 단위면 그 아래 시/군/구가 실제 지역명이라 두 번째 토큰을 쓴다.
 * TourAPI 주소가 항상 이 형식을 따른다는 전제의 best-effort 파싱.
 */
public final class RegionLabelResolver {

    private static final String[] METRO_SUFFIXES = {"특별자치시", "특별시", "광역시"};
    private static final String[] CITY_SUFFIXES = {"시", "군", "구"};

    private RegionLabelResolver() {
    }

    public static String fromTripDetail(TripDetailResponse detail) {
        return detail.days().stream()
                .flatMap(day -> day.items().stream())
                .findFirst()
                .map(item -> fromAddress(item.address()))
                .orElse(null);
    }

    public static String fromAddress(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String[] tokens = address.trim().split("\\s+");
        String first = tokens[0];

        for (String suffix : METRO_SUFFIXES) {
            if (first.endsWith(suffix)) {
                return stripSuffix(first, suffix);
            }
        }
        if (first.endsWith("도") && tokens.length > 1) {
            return stripSuffix(tokens[1], CITY_SUFFIXES);
        }
        return stripSuffix(first, CITY_SUFFIXES);
    }

    private static String stripSuffix(String token, String... suffixes) {
        for (String suffix : suffixes) {
            if (token.length() > suffix.length() && token.endsWith(suffix)) {
                return token.substring(0, token.length() - suffix.length());
            }
        }
        return token;
    }
}
