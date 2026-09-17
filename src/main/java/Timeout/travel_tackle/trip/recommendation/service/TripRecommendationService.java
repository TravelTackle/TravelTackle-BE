package Timeout.travel_tackle.trip.recommendation.service;
import Timeout.travel_tackle.trip.recommendation.repository.TripRecommendationQueryRepository;

import Timeout.travel_tackle.entity.Trip;
import Timeout.travel_tackle.entity.TripRecord;
import Timeout.travel_tackle.entity.UserPreference;
import Timeout.travel_tackle.global.util.UuidConverter;
import Timeout.travel_tackle.preference.repository.UserPreferenceRepository;
import Timeout.travel_tackle.tour.recommendation.PreferenceMapper;
import Timeout.travel_tackle.trip.recommendation.repository.TripRecommendationQueryRepository.ItemSignal;
import Timeout.travel_tackle.trip.recommendation.dto.RecommendedRecordResponse;
import Timeout.travel_tackle.trip.recommendation.dto.RecommendedTripResponse;
import Timeout.travel_tackle.trip.repository.TripPhotoRepository;
import Timeout.travel_tackle.trip.repository.TripRecordRepository;
import Timeout.travel_tackle.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 추천 탭 — 다른 사용자의 공개 계획/기록을 "내 선호도와의 일치 정도"로 정렬해 추천한다.
 * 일치 기준은 방문지(TripItem)에 캐싱된 분류체계(lclsSystm1/2)와 지역코드를,
 * 내 선호도가 매핑하는 코드 집합({@link PreferenceMapper})과 비교한 점수다.
 * 계획 추천과 기록 추천은 같은 점수 로직을 쓰되 결과 목록이 분리된다.
 */
@Service
@RequiredArgsConstructor
public class TripRecommendationService {

    private static final int LCLS1_WEIGHT = 2; // 대분류 일치 = 큰 취향
    private static final int LCLS2_WEIGHT = 1; // 중분류 일치 = 세부 취향
    private static final int REGION_WEIGHT = 1; // 선호 지역 일치

    private final UserPreferenceRepository userPreferenceRepository;
    private final TripRecommendationQueryRepository recommendationQueryRepository;
    private final TripRepository tripRepository;
    private final TripRecordRepository tripRecordRepository;
    private final TripPhotoRepository tripPhotoRepository;

    /** 계획 추천: 취향 일치 점수가 높은 공개 계획 순. */
    @Transactional(readOnly = true)
    public List<RecommendedTripResponse> recommendTrips(String subject, int limit) {
        UUID userId = UuidConverter.fromSubject(subject);
        Map<UUID, Integer> scoreByTrip = scoreTripsFor(userId);
        if (scoreByTrip.isEmpty()) {
            return List.of();
        }

        List<UUID> topTripIds = topTripIds(scoreByTrip, limit);
        Map<UUID, String> thumbnails = resolveThumbnails(topTripIds);

        return tripRepository.findPublishedByIdInWithUser(topTripIds).stream()
                .map(trip -> RecommendedTripResponse.of(
                        trip, thumbnails.get(trip.getId()), scoreByTrip.get(trip.getId())))
                .sorted(byScoreDescThenRecent(RecommendedTripResponse::matchScore,
                        RecommendedTripResponse::createdAt))
                .toList();
    }

    /** 기록 추천: 취향 일치 점수가 높은 계획에 달린 공개 기록 순(계획 추천과 분리). */
    @Transactional(readOnly = true)
    public List<RecommendedRecordResponse> recommendRecords(String subject, int limit) {
        UUID userId = UuidConverter.fromSubject(subject);
        Map<UUID, Integer> scoreByTrip = scoreTripsFor(userId);
        if (scoreByTrip.isEmpty()) {
            return List.of();
        }

        // 점수 있는 계획 중 기록이 있는 것만 추려 점수순으로 정렬한다.
        List<TripRecord> records = tripRecordRepository
                .findPublishedByTripIdInWithTripAndUser(scoreByTrip.keySet());
        if (records.isEmpty()) {
            return List.of();
        }

        Map<UUID, String> thumbnails = resolveThumbnails(
                records.stream().map(r -> r.getTrip().getId()).toList());

        return records.stream()
                .map(record -> RecommendedRecordResponse.of(record,
                        thumbnails.get(record.getTrip().getId()),
                        scoreByTrip.get(record.getTrip().getId())))
                .sorted(byScoreDescThenRecent(RecommendedRecordResponse::matchScore,
                        RecommendedRecordResponse::createdAt))
                .limit(normalizeLimit(limit))
                .toList();
    }

    /** 공개 계획(본인 제외)별 취향 일치 점수. 선호도가 없거나 일치 0이면 빈 맵. */
    private Map<UUID, Integer> scoreTripsFor(UUID userId) {
        Optional<UserPreference> preferenceOpt = userPreferenceRepository.findByUserId(userId);
        if (preferenceOpt.isEmpty()) {
            return Map.of();
        }
        PreferenceSignals signals = PreferenceSignals.from(preferenceOpt.get());
        if (signals.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Integer> scoreByTrip = new HashMap<>();
        for (ItemSignal item : recommendationQueryRepository.findPublishedItemSignals(userId)) {
            int score = signals.score(item);
            if (score > 0) {
                scoreByTrip.merge(item.tripId(), score, Integer::sum);
            }
        }
        return scoreByTrip;
    }

    private List<UUID> topTripIds(Map<UUID, Integer> scoreByTrip, int limit) {
        return scoreByTrip.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(normalizeLimit(limit))
                .map(Map.Entry::getKey)
                .toList();
    }

    private Map<UUID, String> resolveThumbnails(List<UUID> tripIds) {
        if (tripIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> thumbnails = new HashMap<>();
        // (계획ID, 이미지URL)이 업로드순으로 오므로 계획별 첫 사진만 남긴다
        for (Object[] row : tripPhotoRepository.findThumbnailRowsByTripIds(tripIds)) {
            thumbnails.putIfAbsent((UUID) row[0], (String) row[1]);
        }
        return thumbnails;
    }

    private <T> Comparator<T> byScoreDescThenRecent(
            java.util.function.ToIntFunction<T> score,
            java.util.function.Function<T, java.time.LocalDateTime> createdAt) {
        return Comparator.comparingInt(score).reversed()
                .thenComparing(createdAt, Comparator.reverseOrder());
    }

    private int normalizeLimit(int limit) {
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, 50);
    }

    /** 선호도가 매핑하는 매칭 코드 집합. */
    private record PreferenceSignals(Set<String> lcls1, Set<String> lcls2, Set<String> regions) {

        static PreferenceSignals from(UserPreference preference) {
            Set<String> lcls1 = preference.getInterestTags().stream()
                    .map(tag -> PreferenceMapper.toApiParams(tag).lclsSystm1())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Set<String> lcls2 = preference.getInterestTags().stream()
                    .map(tag -> PreferenceMapper.toApiParams(tag).lclsSystm2())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Set<String> regions = preference.getPreferredRegions().stream()
                    .map(PreferenceMapper::toAreaCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            return new PreferenceSignals(lcls1, lcls2, regions);
        }

        boolean isEmpty() {
            return lcls1.isEmpty() && lcls2.isEmpty() && regions.isEmpty();
        }

        int score(ItemSignal item) {
            int score = 0;
            if (StringUtils.hasText(item.lclsSystm1()) && lcls1.contains(item.lclsSystm1())) {
                score += LCLS1_WEIGHT;
            }
            if (StringUtils.hasText(item.lclsSystm2()) && lcls2.contains(item.lclsSystm2())) {
                score += LCLS2_WEIGHT;
            }
            if (StringUtils.hasText(item.regionCode()) && regions.contains(item.regionCode())) {
                score += REGION_WEIGHT;
            }
            return score;
        }
    }
}
