package Timeout.travel_tackle.trip.recommendation.controller;
import Timeout.travel_tackle.trip.recommendation.service.TripRecommendationService;

import Timeout.travel_tackle.trip.recommendation.dto.RecommendedRecordResponse;
import Timeout.travel_tackle.trip.recommendation.dto.RecommendedTripResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@Tag(name = "Recommendation", description = "선호도 기반 추천 탭 API (다른 사용자 계획/기록)")
public class TripRecommendationController {

    private final TripRecommendationService tripRecommendationService;

    @GetMapping("/trips")
    @Operation(summary = "계획 추천 - 내 선호도와 일치하는 공개 계획 (일치 많은 순)")
    public List<RecommendedTripResponse> recommendTrips(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return tripRecommendationService.recommendTrips(jwt.getSubject(), limit);
    }

    @GetMapping("/records")
    @Operation(summary = "기록 추천 - 내 선호도와 일치하는 공개 기록 (일치 많은 순)")
    public List<RecommendedRecordResponse> recommendRecords(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return tripRecommendationService.recommendRecords(jwt.getSubject(), limit);
    }
}
