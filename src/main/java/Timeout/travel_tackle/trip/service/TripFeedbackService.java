package Timeout.travel_tackle.trip.service;

import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.cart.service.CartService;
import Timeout.travel_tackle.cart.service.CartService.CartItemResponse;
import Timeout.travel_tackle.entity.Trip;
import Timeout.travel_tackle.entity.TripDay;
import Timeout.travel_tackle.entity.TripFeedback;
import Timeout.travel_tackle.entity.TripFeedbackLike;
import Timeout.travel_tackle.entity.TripFeedbackRecommendation;
import Timeout.travel_tackle.entity.TripItem;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import Timeout.travel_tackle.entity.Enum.NotificationTarget;
import Timeout.travel_tackle.notification.dto.FeedbackNotificationCommand;
import Timeout.travel_tackle.notification.service.NotificationService;
import Timeout.travel_tackle.trip.repository.TripPhotoRepository;
import Timeout.travel_tackle.tour.dto.TourDtos.ContentDetail;
import Timeout.travel_tackle.tour.service.TourService;
import Timeout.travel_tackle.trip.dto.CreateFeedbackRequest;
import Timeout.travel_tackle.trip.dto.FeedbackRecommendationResponse;
import Timeout.travel_tackle.trip.dto.FeedbackResponse;
import Timeout.travel_tackle.trip.dto.ReceivedFeedbackSummary;
import Timeout.travel_tackle.trip.dto.UpdateFeedbackRequest;
import Timeout.travel_tackle.trip.repository.TripDayRepository;
import Timeout.travel_tackle.trip.repository.TripFeedbackLikeRepository;
import Timeout.travel_tackle.trip.repository.TripFeedbackRecommendationRepository;
import Timeout.travel_tackle.trip.repository.TripFeedbackRepository;
import Timeout.travel_tackle.trip.repository.TripItemRepository;
import Timeout.travel_tackle.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TripFeedbackService {

    private final TripRepository tripRepository;
    private final TripDayRepository tripDayRepository;
    private final TripItemRepository tripItemRepository;
    private final TripFeedbackRepository feedbackRepository;
    private final TripFeedbackLikeRepository feedbackLikeRepository;
    private final TripFeedbackRecommendationRepository recommendationRepository;
    private final UserRepository userRepository;
    private final TourService tourService;
    private final CartService cartService;
    private final TripPhotoRepository tripPhotoRepository;
    private final NotificationService notificationService;

    @Transactional
    public FeedbackResponse create(UUID userId, UUID tripId, CreateFeedbackRequest request) {
        Trip trip = findPublishedTrip(tripId);
        User author = findUser(userId);

        if (trip.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.CANNOT_FEEDBACK_OWN_TRIP);
        }
        if (request.tripDayId() != null && request.tripItemId() != null) {
            throw new CustomException(ErrorCode.FEEDBACK_TARGET_CONFLICT);
        }

        TripDay tripDay = resolveDay(request.tripDayId(), trip);
        TripItem tripItem = resolveItem(request.tripItemId(), trip);

        TripFeedback feedback = feedbackRepository.save(
                new TripFeedback(trip, tripDay, tripItem, author, request.content()));

        List<TripFeedbackRecommendation> recs = buildRecommendations(feedback, request.recommendations());
        recommendationRepository.saveAll(recs);
        notificationService.notifyFeedback(toNotificationCommand(trip.getUser().getId(), trip, author, feedback, tripDay, tripItem));

        return toResponse(feedback, recs, 0L, false);
    }

    // receiver: 참견 알림은 계획 주인, 좋아요 알림은 참견 작성자. actor: 행동한 사람
    private FeedbackNotificationCommand toNotificationCommand(UUID receiverId, Trip trip, User actor, TripFeedback feedback,
                                                              TripDay tripDay, TripItem tripItem) {
        NotificationTarget target = NotificationTarget.TRIP;
        Integer dayNumber = null; // int 와 null 을 삼항으로 섞으면 언박싱 NPE 가 나므로 분기로 나눈다
        if (tripItem != null) {
            target = NotificationTarget.ITEM;
            dayNumber = tripItem.getTripDay().getDayNumber();
        } else if (tripDay != null) {
            target = NotificationTarget.DAY;
            dayNumber = tripDay.getDayNumber();
        }
        String thumbnailUrl = tripPhotoRepository.findThumbnailRowsByTripIds(List.of(trip.getId())).stream()
                .findFirst().map(row -> (String) row[1]).orElse(null);
        return new FeedbackNotificationCommand(
                receiverId, actor.getId(), actor.getName(),
                trip.getId(), trip.getTitle(), thumbnailUrl,
                feedback.getId(), target, dayNumber,
                tripItem != null ? tripItem.getCachedTitle() : null,
                feedback.getContent());
    }

    @Transactional
    public Page<FeedbackResponse> getList(UUID tripId, UUID dayId, UUID itemId,
                                          UUID callerId, Pageable pageable) {
        Trip trip = findTrip(tripId);

        if (!trip.isPublished()) {
            boolean isOwner = callerId != null && trip.getUser().getId().equals(callerId);
            boolean isFeedbackAuthor = callerId != null
                    && feedbackRepository.existsByTripIdAndAuthorId(tripId, callerId);
            if (!isOwner && !isFeedbackAuthor) {
                throw new CustomException(ErrorCode.TRIP_NOT_PUBLISHED);
            }
        }

        // 소유자가 조회하면 먼저 읽음 처리 후 DB에서 새로 조회 (isRead 응답값 일관성)
        if (callerId != null && trip.getUser().getId().equals(callerId)) {
            feedbackRepository.markAllReadByTripId(tripId);
        }

        Page<TripFeedback> page = fetchPage(tripId, dayId, itemId, pageable);
        return enrichWithLikes(page, callerId);
    }

    @Transactional
    public Page<FeedbackResponse> getAll(UUID tripId, UUID callerId, Pageable pageable) {
        Trip trip = findTrip(tripId);

        if (!trip.isPublished()) {
            boolean isOwner = callerId != null && trip.getUser().getId().equals(callerId);
            boolean isFeedbackAuthor = callerId != null
                    && feedbackRepository.existsByTripIdAndAuthorId(tripId, callerId);
            if (!isOwner && !isFeedbackAuthor) {
                throw new CustomException(ErrorCode.TRIP_NOT_PUBLISHED);
            }
        }

        if (callerId != null && trip.getUser().getId().equals(callerId)) {
            feedbackRepository.markAllReadByTripId(tripId);
        }

        Page<TripFeedback> page = feedbackRepository.findAllByTripId(tripId, pageable);
        return enrichWithLikes(page, callerId);
    }

    // 목록 조회 공통 — 페이지에 담긴 피드백들의 좋아요 수/내가 눌렀는지 여부를 한 번에 채워 넣는다 (N+1 방지)
    private Page<FeedbackResponse> enrichWithLikes(Page<TripFeedback> page, UUID callerId) {
        List<UUID> feedbackIds = page.getContent().stream().map(TripFeedback::getId).toList();
        Map<UUID, Long> likeCounts = toMap(feedbackLikeRepository.countGroupByFeedbackIds(feedbackIds));
        Set<UUID> likedByMe = (callerId == null || feedbackIds.isEmpty())
                ? Set.of()
                : new HashSet<>(feedbackLikeRepository.findLikedFeedbackIds(feedbackIds, callerId));

        return page.map(f -> toResponse(
                f, recommendationRepository.findAllByFeedbackId(f.getId()),
                likeCounts.getOrDefault(f.getId(), 0L), likedByMe.contains(f.getId())));
    }

    @Transactional
    public FeedbackResponse update(UUID userId, UUID tripId, UUID feedbackId,
                                   UpdateFeedbackRequest request) {
        TripFeedback feedback = findFeedbackInTrip(feedbackId, tripId);
        if (feedback.getAuthor() == null || !feedback.getAuthor().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FEEDBACK_ACCESS_DENIED);
        }

        feedback.updateContent(request.content());

        // 추천 관광지 전체 교체
        recommendationRepository.deleteAllByFeedbackId(feedbackId);
        List<TripFeedbackRecommendation> recs = buildRecommendations(feedback,
                request.recommendations() != null ? request.recommendations() : List.of());
        recommendationRepository.saveAll(recs);

        long likeCount = countLikes(feedbackId);
        boolean likedByMe = feedbackLikeRepository.existsByFeedbackAndUser(feedback, findUser(userId));
        return toResponse(feedback, recs, likeCount, likedByMe);
    }

    @Transactional
    public void delete(UUID userId, UUID tripId, UUID feedbackId) {
        TripFeedback feedback = findFeedbackInTrip(feedbackId, tripId);
        boolean isAuthor = feedback.getAuthor() != null && feedback.getAuthor().getId().equals(userId);
        boolean isTripOwner = feedback.getTrip().getUser().getId().equals(userId);
        if (!isAuthor && !isTripOwner) {
            throw new CustomException(ErrorCode.FEEDBACK_ACCESS_DENIED);
        }
        feedbackLikeRepository.deleteAllByFeedbackId(feedbackId);
        recommendationRepository.deleteAllByFeedbackId(feedbackId);
        feedbackRepository.delete(feedback);
    }

    @Transactional
    public FeedbackResponse likeFeedback(UUID userId, UUID tripId, UUID feedbackId) {
        TripFeedback feedback = findFeedbackInTrip(feedbackId, tripId);
        User user = findUser(userId);
        if (feedbackLikeRepository.existsByFeedbackAndUser(feedback, user)) {
            throw new CustomException(ErrorCode.FEEDBACK_ALREADY_LIKED);
        }
        feedbackLikeRepository.save(new TripFeedbackLike(feedback, user));
        if (feedback.getAuthor() != null) { // 탈퇴로 익명화된 참견은 받을 사람이 없다
            notificationService.notifyFeedbackLike(toNotificationCommand(feedback.getAuthor().getId(),
                    feedback.getTrip(), user, feedback, feedback.getTripDay(), feedback.getTripItem()));
        }
        return toResponse(feedback, recommendationRepository.findAllByFeedbackId(feedbackId), countLikes(feedbackId), true);
    }

    @Transactional
    public FeedbackResponse unlikeFeedback(UUID userId, UUID tripId, UUID feedbackId) {
        TripFeedback feedback = findFeedbackInTrip(feedbackId, tripId);
        User user = findUser(userId);
        TripFeedbackLike like = feedbackLikeRepository.findByFeedbackAndUser(feedback, user)
                .orElseThrow(() -> new CustomException(ErrorCode.FEEDBACK_LIKE_NOT_FOUND));
        feedbackLikeRepository.delete(like);
        return toResponse(feedback, recommendationRepository.findAllByFeedbackId(feedbackId), countLikes(feedbackId), false);
    }

    private long countLikes(UUID feedbackId) {
        return feedbackLikeRepository.countGroupByFeedbackIds(List.of(feedbackId)).stream()
                .findFirst().map(row -> (Long) row[1]).orElse(0L);
    }

    @Transactional
    public CartItemResponse addRecommendationToCart(UUID userId, UUID tripId, UUID recommendationId) {
        TripFeedbackRecommendation rec = recommendationRepository.findByIdWithTripOwner(recommendationId)
                .orElseThrow(() -> new CustomException(ErrorCode.FEEDBACK_RECOMMENDATION_NOT_FOUND));
        Trip trip = rec.getFeedback().getTrip();
        if (!trip.getId().equals(tripId)) {
            throw new CustomException(ErrorCode.FEEDBACK_RECOMMENDATION_NOT_FOUND);
        }
        if (!trip.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.TRIP_ACCESS_DENIED);
        }
        return cartService.addFromCachedData(userId,
                rec.getTourApiContentId(), rec.getCachedTitle(),
                rec.getCachedImageUrl(), rec.getCachedAreaCode(), rec.getCachedAddress());
    }

    @Transactional(readOnly = true)
    public List<ReceivedFeedbackSummary> getReceivedSummary(UUID userId) {
        User user = findUser(userId);
        List<Trip> myTrips = tripRepository.findAllByUserOrderByCreatedAtDesc(user);
        if (myTrips.isEmpty()) {
            return List.of();
        }

        List<UUID> tripIds = myTrips.stream().map(Trip::getId).toList();

        Map<UUID, Long> totalMap = toMap(feedbackRepository.countGroupByTripIds(tripIds));
        Map<UUID, Long> unreadMap = toMap(feedbackRepository.countUnreadGroupByTripIds(tripIds));
        Map<UUID, LocalDateTime> latestMap = toLatestMap(
                feedbackRepository.findLatestCreatedAtGroupByTripIds(tripIds));

        List<ReceivedFeedbackSummary> result = new ArrayList<>();
        for (Trip trip : myTrips) {
            long total = totalMap.getOrDefault(trip.getId(), 0L);
            if (total == 0) continue;

            LocalDateTime latestFeedbackAt = latestMap.get(trip.getId());
            LocalDateTime dismissedAt = trip.getFeedbackNotificationDismissedAt();
            // 지운 뒤로 새 참견이 안 달렸으면 모아보기에서 계속 숨긴다 — 새 참견이 달리면 자동으로 다시 노출
            if (dismissedAt != null && (latestFeedbackAt == null || !latestFeedbackAt.isAfter(dismissedAt))) {
                continue;
            }

            result.add(new ReceivedFeedbackSummary(
                    trip.getId(),
                    trip.getTitle(),
                    total,
                    unreadMap.getOrDefault(trip.getId(), 0L),
                    latestFeedbackAt
            ));
        }
        return result;
    }

    /**
     * 참견 알림함에서 "지우기" — 실제 삭제는 아니고, 지운 시각을 저장해 모아보기 목록에서
     * 숨긴다. 기존 markAllReadByTripId를 재사용해 읽음 처리도 함께 한다(계획 상세 진입 시와 동일 효과).
     * 지운 이후 새 참견이 달리면 getReceivedSummary가 자동으로 다시 노출한다.
     */
    @Transactional
    public void dismissNotifications(UUID userId, UUID tripId) {
        Trip trip = findTrip(tripId);
        if (!trip.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.TRIP_ACCESS_DENIED);
        }
        feedbackRepository.markAllReadByTripId(tripId);
        trip.dismissFeedbackNotifications();
    }

    // --- 내부 헬퍼 ---

    private Page<TripFeedback> fetchPage(UUID tripId, UUID dayId, UUID itemId, Pageable pageable) {
        if (itemId != null) {
            return feedbackRepository.findAllByTripItemId(itemId, pageable);
        }
        if (dayId != null) {
            return feedbackRepository.findAllByTripDayId(dayId, pageable);
        }
        return feedbackRepository.findAllByTripIdAndTripDayIsNullAndTripItemIsNull(tripId, pageable);
    }

    private List<TripFeedbackRecommendation> buildRecommendations(
            TripFeedback feedback,
            List<CreateFeedbackRequest.RecommendationRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<TripFeedbackRecommendation> recs = new ArrayList<>();
        for (CreateFeedbackRequest.RecommendationRequest req : requests) {
            ContentDetail detail = tourService.getContentDetail(req.contentId());
            recs.add(new TripFeedbackRecommendation(
                    feedback,
                    detail.contentId(),
                    detail.title(),
                    detail.imageUrl(),
                    detail.areaCode(),
                    detail.address()
            ));
        }
        return recs;
    }

    private FeedbackResponse toResponse(TripFeedback feedback,
                                        List<TripFeedbackRecommendation> recs,
                                        long likeCount, boolean likedByMe) {
        List<FeedbackRecommendationResponse> recResponses = recs.stream()
                .map(FeedbackRecommendationResponse::from).toList();
        return FeedbackResponse.of(feedback, recResponses, likeCount, likedByMe);
    }

    private TripDay resolveDay(UUID dayId, Trip trip) {
        if (dayId == null) return null;
        TripDay day = tripDayRepository.findById(dayId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRIP_DAY_NOT_FOUND));
        if (!day.getTrip().getId().equals(trip.getId())) {
            throw new CustomException(ErrorCode.TRIP_DAY_NOT_FOUND);
        }
        return day;
    }

    private TripItem resolveItem(UUID itemId, Trip trip) {
        if (itemId == null) return null;
        TripItem item = tripItemRepository.findById(itemId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRIP_ITEM_NOT_FOUND));
        if (!item.getTripDay().getTrip().getId().equals(trip.getId())) {
            throw new CustomException(ErrorCode.TRIP_ITEM_NOT_FOUND);
        }
        return item;
    }

    private Trip findTrip(UUID tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRIP_NOT_FOUND));
    }

    private Trip findPublishedTrip(UUID tripId) {
        Trip trip = findTrip(tripId);
        if (!trip.isPublished()) {
            throw new CustomException(ErrorCode.TRIP_NOT_PUBLISHED);
        }
        return trip;
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHENTICATED));
    }

    private TripFeedback findFeedbackInTrip(UUID feedbackId, UUID tripId) {
        return feedbackRepository.findByIdAndTripId(feedbackId, tripId)
                .orElseThrow(() -> new CustomException(ErrorCode.FEEDBACK_NOT_FOUND));
    }

    private Map<UUID, Long> toMap(List<Object[]> rows) {
        Map<UUID, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((UUID) row[0], (Long) row[1]);
        }
        return map;
    }

    private Map<UUID, LocalDateTime> toLatestMap(List<Object[]> rows) {
        Map<UUID, LocalDateTime> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((UUID) row[0], (LocalDateTime) row[1]);
        }
        return map;
    }
}
