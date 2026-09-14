package Timeout.travel_tackle.tour.service;

import org.springframework.util.StringUtils;

/** 대화/요청 언어 코드 → TourAPI 언어별 서비스. 챗봇(TourismTools)과 REST(TourController)가 공유한다. */
public final class TourLanguageResolver {

    public static final String DEFAULT_SERVICE = "KorService2";

    private TourLanguageResolver() {
    }

    public static String toService(String language) {
        if (!StringUtils.hasText(language)) {
            return DEFAULT_SERVICE;
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
            default -> DEFAULT_SERVICE;
        };
    }
}
