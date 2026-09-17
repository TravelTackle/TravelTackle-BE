package Timeout.travel_tackle.tour.recommendation;

import Timeout.travel_tackle.entity.Enum.InterestTag;
import Timeout.travel_tackle.entity.Enum.PreferredRegion;

public final class PreferenceMapper {

    private PreferenceMapper() {
    }

    public record TourApiParams(String contentTypeId, String lclsSystm1, String lclsSystm2) {
    }

    public static TourApiParams toApiParams(InterestTag tag) {
        return switch (tag) {
            case FOOD        -> new TourApiParams("39", "FD",  null);
            case CAFE        -> new TourApiParams("39", "FD",  "FD05");
            case NIGHTLIFE   -> new TourApiParams("39", "FD",  "FD04");
            case SHOPPING    -> new TourApiParams("38", "SH",  null);
            case FESTIVAL    -> new TourApiParams("15", "EV",  "EV01");
            case HISTORY     -> new TourApiParams("12", "HS",  null);
            case NATURE      -> new TourApiParams("12", "NA",  null);
            case WELLBEING   -> new TourApiParams("12", "EX",  "EX05");
            case ART         -> new TourApiParams("14", "VE",  "VE07");
            case ACTIVITY    -> new TourApiParams("28", "LS",  null);
            case K_POP       -> new TourApiParams("15", "EV",  "EV02");
            case PHOTOGRAPHY -> new TourApiParams("12", "VE",  "VE01");
        };
    }

    public static String toLDongRegnCd(PreferredRegion region) {
        return switch (region) {
            case SEOUL       -> "11";
            case INCHEON     -> "28";
            case BUSAN       -> "26";
            case GANGWON     -> "51";
            case CHUNGCHEONG -> "43";
            case GYEONGBUK, GYEONGJU -> "47";
            case JEONJU      -> "52";
            case JEONNAM     -> "46";
            case JEJU        -> "50";
            case OTHER       -> null;
        };
    }

    // searchFestival2는 areaCode(구형)를 아직 사용 중인 기존 메서드 재활용
    public static String toAreaCode(PreferredRegion region) {
        return switch (region) {
            case SEOUL       -> "1";
            case INCHEON     -> "2";
            case BUSAN       -> "6";
            case GANGWON     -> "32";
            case CHUNGCHEONG -> "33";
            case GYEONGBUK, GYEONGJU -> "35";
            case JEONJU      -> "37";
            case JEONNAM     -> "38";
            case JEJU        -> "39";
            case OTHER       -> null;
        };
    }
}
