package Timeout.travel_tackle.trip.service;

import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.entity.Trip;
import Timeout.travel_tackle.entity.TripRecord;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import Timeout.travel_tackle.trip.dto.FeedItemResponse;
import Timeout.travel_tackle.trip.dto.FeedSort;
import Timeout.travel_tackle.trip.dto.PublicTripDetailResponse;
import Timeout.travel_tackle.trip.dto.RegionCountResponse;
import Timeout.travel_tackle.trip.dto.TripDetailResponse;
import Timeout.travel_tackle.trip.dto.TripRecordResponse;
import Timeout.travel_tackle.trip.dto.UserProfileResponse;
import Timeout.travel_tackle.trip.repository.SavedTripRepository;
import Timeout.travel_tackle.trip.repository.TripFeedbackRepository;
import Timeout.travel_tackle.trip.repository.TripPhotoRepository;
import Timeout.travel_tackle.trip.repository.TripQueryRepository;
import Timeout.travel_tackle.trip.repository.TripRecordRepository;
import Timeout.travel_tackle.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedService {

    private final TripRepository tripRepository;
    private final TripRecordRepository tripRecordRepository;
    private final TripPhotoRepository tripPhotoRepository;
    private final TripQueryRepository tripQueryRepository;
    private final TripFeedbackRepository tripFeedbackRepository;
    private final SavedTripRepository savedTripRepository;
    private final UserRepository userRepository;

    /**
     * 공개된 여행 피드 — 최신순(LATEST) 또는 인기순(POPULAR: 참견 수 + 스크랩 수) 페이지네이션.
     * Trip 하나당 PLAN 카드 1개는 항상, 기록(TripRecord)이 있으면 RECORD 카드를 추가로 낸다.
     * 그래서 응답 개수가 요청한 size보다 많을 수 있다 (Trip 1개 -> 최대 2개 항목) — 실사용자 규모가
     * 커지면 페이지네이션을 다시 손봐야 하는 알려진 한계.
     */
    @Transactional(readOnly = true)
    public Page<FeedItemResponse> getFeed(Pageable pageable, FeedSort sort) {
        return getFeed(pageable, sort, null, null);
    }

    /**
     * keyword가 있으면 계획 제목/기록 제목·내용을 검색한 결과를 sort(RELEVANCE 기본)로 정렬해 반환한다.
     */
    @Transactional(readOnly = true)
    public Page<FeedItemResponse> getFeed(Pageable pageable, FeedSort sort, String keyword) {
        return getFeed(pageable, sort, keyword, null);
    }

    /**
     * userId가 있으면(로그인 상태) 각 항목에 내가 스크랩했는지(saved) 여부를 채워 준다.
     * GET /api/feed는 permitAll이라 비로그인 요청은 userId=null로 들어오고, 이 경우 saved는 항상 false.
     */
    @Transactional(readOnly = true)
    public Page<FeedItemResponse> getFeed(Pageable pageable, FeedSort sort, String keyword, UUID userId) {
        Page<Trip> trips = StringUtils.hasText(keyword)
                ? tripQueryRepository.searchPublishedTrips(keyword.trim(), sort, pageable)
                : (sort == FeedSort.POPULAR
                        ? tripRepository.findPublishedWithUserOrderByPopularity(pageable)
                        : tripRepository.findPublishedWithUser(pageable));
        return buildFeedPage(trips, pageable, userId);
    }

    /**
     * 마이페이지(/mypage)가 아니라 이 엔드포인트로 타인의 공개 프로필을 열람할 때 쓰는 피드 —
     * 그 사용자의 공개(published) 계획/기록만 나가고, keyword 검색은 지원하지 않는다.
     */
    @Transactional(readOnly = true)
    public Page<FeedItemResponse> getUserFeed(UUID targetUserId, Pageable pageable, FeedSort sort, UUID viewerUserId) {
        User targetUser = findUser(targetUserId);
        Page<Trip> trips = sort == FeedSort.POPULAR
                ? tripRepository.findPublishedByUserOrderByPopularity(targetUser, pageable)
                : tripRepository.findPublishedByUser(targetUser, pageable);
        return buildFeedPage(trips, pageable, viewerUserId);
    }

    /**
     * 공개 프로필 요약(이름/프로필사진/공개 계획·기록 수) — 이메일·크레딧 등 비공개 정보는 담지 않는다.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(UUID targetUserId) {
        User targetUser = findUser(targetUserId);
        long planCount = tripRepository.countByUserAndPublishedTrue(targetUser);
        long recordCount = tripRecordRepository.countByTrip_UserAndTrip_PublishedTrue(targetUser);
        return UserProfileResponse.of(targetUser, planCount, recordCount);
    }

    private Page<FeedItemResponse> buildFeedPage(Page<Trip> trips, Pageable pageable, UUID viewerUserId) {
        List<UUID> tripIds = trips.getContent().stream().map(Trip::getId).toList();
        Map<UUID, String> thumbnails = resolveThumbnails(trips.getContent());
        Map<UUID, Long> feedbackCounts = resolveFeedbackCounts(tripIds);
        Map<UUID, Long> saveCounts = resolveSaveCounts(tripIds);
        Map<UUID, TripRecord> records = resolveRecords(tripIds);
        Map<UUID, UUID> savedTripIdsByOriginal = resolveSavedTripIdsByOriginal(viewerUserId, tripIds);

        List<FeedItemResponse> items = trips.getContent().stream()
                .flatMap(trip -> buildFeedItems(trip, thumbnails, feedbackCounts, saveCounts, records, savedTripIdsByOriginal).stream())
                .toList();

        return new PageImpl<>(items, pageable, trips.getTotalElements());
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 기간 내 공개 계획에 등장하는 지역을 센다. 한 계획에 같은 지역 일정이 여러 개여도 그 지역은 1번만 세고,
     * 용인·수원처럼 여러 지역이 섞이면 각 지역에 1씩 더한다. 계획 수 내림차순, 동점은 지역명 오름차순, 상위 size 개.
     * 기간은 계획 생성일(createdAt) 기준이며 from/to 는 날짜 단위로 양끝 포함, null 이면 무제한.
     */
    @Transactional(readOnly = true)
    public List<RegionCountResponse> getRegionCounts(LocalDate from, LocalDate to, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        LocalDateTime fromAt = from == null ? null : from.atStartOfDay();
        LocalDateTime toAt = to == null ? null : to.plusDays(1).atStartOfDay().minusNanos(1);

        Map<String, Long> counts = new HashMap<>();
        for (List<String> addresses : tripQueryRepository.findItemAddressesOfPublishedTrips(fromAt, toAt).values()) {
            addresses.stream()
                    .map(RegionLabelResolver::fromAddress)
                    .filter(region -> region != null && !region.isBlank())
                    .distinct()
                    .forEach(region -> counts.merge(region, 1L, Long::sum));
        }

        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(size)
                .map(entry -> new RegionCountResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * 공개된 여행의 상세(일정 + 기록 + 작성자 + 피드백 수) 조회.
     */
    @Transactional(readOnly = true)
    public PublicTripDetailResponse getPublicTripDetail(UUID tripId) {
        return getPublicTripDetail(tripId, null);
    }

    @Transactional(readOnly = true)
    public PublicTripDetailResponse getPublicTripDetail(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findPublishedDetailById(tripId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRIP_NOT_PUBLISHED));

        TripDetailResponse detail = tripQueryRepository.findDetail(trip);
        TripRecordResponse record = tripRecordRepository.findByTrip(trip)
                .map(r -> TripRecordResponse.of(r,
                        tripPhotoRepository.findAllByRecordOrderByUploadedAtAsc(r)))
                .orElse(null);

        long feedbackCount = tripFeedbackRepository.countGroupByTripIds(List.of(tripId))
                .stream().findFirst().map(row -> (Long) row[1]).orElse(0L);
        UUID savedTripId = resolveSavedTripIdsByOriginal(userId, List.of(tripId)).get(tripId);
        long saveCount = resolveSaveCounts(List.of(tripId)).getOrDefault(tripId, 0L);

        return PublicTripDetailResponse.of(trip, RegionLabelResolver.fromTripDetail(detail), detail.days(), record, feedbackCount, savedTripId, saveCount);
    }

    private List<FeedItemResponse> buildFeedItems(
            Trip trip, Map<UUID, String> thumbnails, Map<UUID, Long> feedbackCounts,
            Map<UUID, Long> saveCounts, Map<UUID, TripRecord> records, Map<UUID, UUID> savedTripIdsByOriginal
    ) {
        String thumbnailUrl = thumbnails.get(trip.getId());
        long feedbackCount = feedbackCounts.getOrDefault(trip.getId(), 0L);
        long saveCount = saveCounts.getOrDefault(trip.getId(), 0L);
        UUID savedTripId = savedTripIdsByOriginal.get(trip.getId());
        TripDetailResponse detail = tripQueryRepository.findDetail(trip);
        String region = RegionLabelResolver.fromTripDetail(detail);

        List<FeedItemResponse> items = new ArrayList<>();
        items.add(FeedItemResponse.ofPlan(trip, thumbnailUrl, feedbackCount, saveCount, savedTripId, region, detail.days()));

        TripRecord record = records.get(trip.getId());
        if (record != null) {
            items.add(FeedItemResponse.ofRecord(trip, record, thumbnailUrl, feedbackCount, saveCount, savedTripId, region));
        }
        return items;
    }

    private Map<UUID, UUID> resolveSavedTripIdsByOriginal(UUID userId, List<UUID> tripIds) {
        if (userId == null || tripIds.isEmpty()) {
            return Map.of();
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Map.of();
        }
        Map<UUID, UUID> savedTripIdsByOriginal = new HashMap<>();
        for (Object[] row : savedTripRepository.findSavedTripIdsByOriginalTripIds(user, tripIds)) {
            savedTripIdsByOriginal.put((UUID) row[0], (UUID) row[1]);
        }
        return savedTripIdsByOriginal;
    }

    private Map<UUID, TripRecord> resolveRecords(List<UUID> tripIds) {
        if (tripIds.isEmpty()) {
            return Map.of();
        }
        return tripRecordRepository.findPublishedByTripIdInWithTripAndUser(tripIds).stream()
                .collect(Collectors.toMap(r -> r.getTrip().getId(), r -> r));
    }

    private Map<UUID, String> resolveThumbnails(List<Trip> trips) {
        if (trips.isEmpty()) {
            return Map.of();
        }
        List<UUID> tripIds = trips.stream().map(Trip::getId).toList();
        Map<UUID, String> thumbnails = new HashMap<>();
        for (Object[] row : tripPhotoRepository.findThumbnailRowsByTripIds(tripIds)) {
            thumbnails.putIfAbsent((UUID) row[0], (String) row[1]);
        }
        return thumbnails;
    }

    private Map<UUID, Long> resolveSaveCounts(List<UUID> tripIds) {
        if (tripIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : savedTripRepository.countGroupByOriginalTripIds(tripIds)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    private Map<UUID, Long> resolveFeedbackCounts(List<UUID> tripIds) {
        if (tripIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : tripFeedbackRepository.countGroupByTripIds(tripIds)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }
}
