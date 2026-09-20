package Timeout.travel_tackle.trip;

import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.cart.repository.CartItemRepository;
import Timeout.travel_tackle.entity.CartItem;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import Timeout.travel_tackle.tour.dto.TourDtos.ContentDetail;
import Timeout.travel_tackle.tour.service.TourService;
import Timeout.travel_tackle.trip.dto.AddTripItemRequest;
import Timeout.travel_tackle.trip.dto.CreateFeedbackRequest;
import Timeout.travel_tackle.trip.dto.CreateFeedbackRequest.RecommendationRequest;
import Timeout.travel_tackle.trip.dto.CreateTripRequest;
import Timeout.travel_tackle.trip.dto.FeedbackResponse;
import Timeout.travel_tackle.trip.dto.ReceivedFeedbackSummary;
import Timeout.travel_tackle.trip.dto.TripDetailResponse;
import Timeout.travel_tackle.trip.dto.UpdateFeedbackRequest;
import Timeout.travel_tackle.trip.dto.UpdateTripRequest;
import Timeout.travel_tackle.trip.repository.TripFeedbackLikeRepository;
import Timeout.travel_tackle.trip.repository.TripFeedbackRepository;
import Timeout.travel_tackle.trip.service.TripFeedbackService;
import Timeout.travel_tackle.trip.service.TripService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class TripFeedbackServiceTests {

    @Autowired TripFeedbackService feedbackService;
    @Autowired TripService tripService;
    @Autowired TripFeedbackRepository feedbackRepository;
    @Autowired TripFeedbackLikeRepository feedbackLikeRepository;
    @Autowired UserRepository userRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired EntityManager entityManager;

    // TourAPI는 외부 의존이므로 목으로 대체
    @MockitoBean TourService tourService;

    private User owner;    // 여행 계획 소유자 (피드백 받는 사람)
    private User reviewer; // 피드백 작성자
    private User other;    // 무관한 제3자

    private UUID tripId;
    private UUID dayId;
    private UUID itemId;

    private static final Pageable DEFAULT_PAGE =
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

    @BeforeEach
    void setUp() {
        owner    = userRepository.save(new User("owner@fb.test",    "계획자", "KR"));
        reviewer = userRepository.save(new User("reviewer@fb.test", "리뷰어", "KR"));
        other    = userRepository.save(new User("other@fb.test",    "제3자", "KR"));

        LocalDate date = LocalDate.of(2026, 7, 1);
        tripId = tripService.createTrip(owner.getId(),
                new CreateTripRequest("강릉 여행", date, date.plusDays(1))).id();

        TripDetailResponse detail = tripService.getTripDetail(owner.getId(), tripId);
        dayId = detail.days().getFirst().id();

        CartItem cart = cartItemRepository.save(
                new CartItem(owner, "125266", "경포대", "img.jpg", "32", "12", null, null, null, null));
        itemId = tripService.addTripItem(owner.getId(), tripId, dayId,
                new AddTripItemRequest(cart.getId(), null, null)).id();

        // 공개 조건: 모든 일차에 일정 1개 이상 — 2일차에도 하나 채운다
        CartItem cartForDay2 = cartItemRepository.save(
                new CartItem(owner, "125267", "정동진", "img2.jpg", "32", "12", null, null, null, null));
        tripService.addTripItem(owner.getId(), tripId, detail.days().get(1).id(),
                new AddTripItemRequest(cartForDay2.getId(), null, null));

        tripService.publishTrip(owner.getId(), tripId, null);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 피드백 주는 사람 (Reviewer) 흐름
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void feedbackAuthorCarriesProfileImage() {
        reviewer.changeProfileImage("https://cdn.test/profiles/r/1.jpg");
        entityManager.flush();
        FeedbackResponse result = feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("사진 확인", null, null, List.of()));
        assertEquals("https://cdn.test/profiles/r/1.jpg", result.author().profileImageUrl());
    }

    @Test
    void createsTripLevelFeedback() {
        FeedbackResponse result = feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("전체적으로 좋네요", null, null, List.of()));

        assertEquals("전체적으로 좋네요", result.content());
        assertNull(result.tripDayId());
        assertNull(result.tripItemId());
        assertEquals("리뷰어", result.author().name());
    }

    @Test
    void createsDayLevelFeedback() {
        FeedbackResponse result = feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("1일차 일정이 빡빡해요", dayId, null, List.of()));

        assertEquals(dayId, result.tripDayId());
        assertNull(result.tripItemId());
    }

    @Test
    void createsItemLevelFeedback() {
        FeedbackResponse result = feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("경포대 대신 다른 곳을", null, itemId, List.of()));

        assertNull(result.tripDayId());
        assertEquals(itemId, result.tripItemId());
    }

    @Test
    void attachesRecommendedAttractionToFeedback() {
        when(tourService.getContentDetail("999111")).thenReturn(
                new ContentDetail("999111", "12", "정동진", "강릉시", null,
                        "32", "3", null, null, null, "img2.jpg",
                        128.9, 37.6, null, null, null, List.of(), null, null, null, null));

        FeedbackResponse result = feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("여기가 더 좋아요", null, null,
                        List.of(new RecommendationRequest("999111"))));

        assertEquals(1, result.recommendations().size());
        assertEquals("정동진", result.recommendations().getFirst().title());
        assertEquals("999111", result.recommendations().getFirst().contentId());
    }

    @Test
    void rejectsOwnTripFeedback() {
        CustomException ex = assertThrows(CustomException.class, () ->
                feedbackService.create(owner.getId(), tripId,
                        new CreateFeedbackRequest("내 여행에 피드백", null, null, List.of())));

        assertEquals(ErrorCode.CANNOT_FEEDBACK_OWN_TRIP, ex.getErrorCode());
    }

    @Test
    void rejectsFeedbackOnUnpublishedTrip() {
        tripService.unpublishTrip(owner.getId(), tripId);

        CustomException ex = assertThrows(CustomException.class, () ->
                feedbackService.create(reviewer.getId(), tripId,
                        new CreateFeedbackRequest("비공개 피드백", null, null, List.of())));

        assertEquals(ErrorCode.TRIP_NOT_PUBLISHED, ex.getErrorCode());
    }

    @Test
    void rejectsDayAndItemConflict() {
        CustomException ex = assertThrows(CustomException.class, () ->
                feedbackService.create(reviewer.getId(), tripId,
                        new CreateFeedbackRequest("충돌", dayId, itemId, List.of())));

        assertEquals(ErrorCode.FEEDBACK_TARGET_CONFLICT, ex.getErrorCode());
    }

    @Test
    void updatesContentAndReplacesRecommendations() {
        when(tourService.getContentDetail("AAA")).thenReturn(stubContent("AAA", "장소A"));
        when(tourService.getContentDetail("BBB")).thenReturn(stubContent("BBB", "장소B"));

        UUID feedbackId = feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("원문",  null, null,
                        List.of(new RecommendationRequest("AAA")))).id();

        FeedbackResponse updated = feedbackService.update(reviewer.getId(), tripId, feedbackId,
                new UpdateFeedbackRequest("수정문", List.of(new RecommendationRequest("BBB"))));

        assertEquals("수정문", updated.content());
        assertEquals(1, updated.recommendations().size());
        assertEquals("장소B", updated.recommendations().getFirst().title());
    }

    @Test
    void rejectsUpdateByNonAuthor() {
        UUID feedbackId = feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("남의 피드백", null, null, List.of())).id();

        CustomException ex = assertThrows(CustomException.class, () ->
                feedbackService.update(other.getId(), tripId, feedbackId,
                        new UpdateFeedbackRequest("무단 수정", List.of())));

        assertEquals(ErrorCode.FEEDBACK_ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    void tripOwnerCanDeleteReviewerFeedback() {
        UUID feedbackId = feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("악성 피드백", null, null, List.of())).id();

        // 소유자가 타인 피드백 삭제
        feedbackService.delete(owner.getId(), tripId, feedbackId);

        assertTrue(feedbackRepository.findById(feedbackId).isEmpty());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 피드백 받는 사람 (Trip 소유자) 흐름
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void receivedSummaryShowsCorrectUnreadCount() {
        feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("피드백1", null, null, List.of()));
        feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("피드백2", null, null, List.of()));

        entityManager.flush();
        entityManager.clear();

        List<ReceivedFeedbackSummary> summary = feedbackService.getReceivedSummary(owner.getId());

        assertEquals(1, summary.size());
        assertEquals(tripId, summary.getFirst().tripId());
        assertEquals(2, summary.getFirst().totalFeedbackCount());
        assertEquals(2, summary.getFirst().unreadCount());
    }

    @Test
    void ownerReadingListMarksAllFeedbackRead() {
        feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("피드백1", null, null, List.of()));
        feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("피드백2", null, null, List.of()));

        entityManager.flush();
        entityManager.clear();

        // 소유자가 목록 조회 → 자동 읽음 처리
        feedbackService.getList(tripId, null, null, owner.getId(), DEFAULT_PAGE);

        entityManager.flush();
        entityManager.clear();

        // 이후 모아보기 미읽음 수 = 0
        List<ReceivedFeedbackSummary> summary = feedbackService.getReceivedSummary(owner.getId());
        assertEquals(0, summary.getFirst().unreadCount());
    }

    @Test
    void dismissingNotificationsHidesTripFromReceivedSummaryAndMarksRead() {
        feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("피드백1", null, null, List.of()));

        entityManager.flush();
        entityManager.clear();

        feedbackService.dismissNotifications(owner.getId(), tripId);

        entityManager.flush();
        entityManager.clear();

        List<ReceivedFeedbackSummary> summary = feedbackService.getReceivedSummary(owner.getId());
        assertTrue(summary.isEmpty());
    }

    @Test
    void newFeedbackAfterDismissMakesTripReappearInReceivedSummary() {
        feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("피드백1", null, null, List.of()));

        entityManager.flush();
        entityManager.clear();

        feedbackService.dismissNotifications(owner.getId(), tripId);

        entityManager.flush();
        entityManager.clear();

        feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("지운 뒤 새 피드백", null, null, List.of()));

        entityManager.flush();
        entityManager.clear();

        List<ReceivedFeedbackSummary> summary = feedbackService.getReceivedSummary(owner.getId());
        assertEquals(1, summary.size());
        assertEquals(2, summary.getFirst().totalFeedbackCount());
        assertEquals(1, summary.getFirst().unreadCount());
    }

    @Test
    void nonOwnerCannotDismissNotifications() {
        feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("피드백1", null, null, List.of()));

        CustomException ex = assertThrows(CustomException.class,
                () -> feedbackService.dismissNotifications(reviewer.getId(), tripId));
        assertEquals(ErrorCode.TRIP_ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    void feedbackListIsFilteredByTargetType() {
        feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("전체 피드백", null, null, List.of()));
        feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("아이템 피드백", null, itemId, List.of()));

        Page<FeedbackResponse> tripLevel = feedbackService.getList(
                tripId, null, null, null, DEFAULT_PAGE);
        Page<FeedbackResponse> itemLevel = feedbackService.getList(
                tripId, null, itemId, null, DEFAULT_PAGE);

        assertEquals(1, tripLevel.getTotalElements());
        assertEquals("전체 피드백", tripLevel.getContent().getFirst().content());
        assertEquals(1, itemLevel.getTotalElements());
        assertEquals("아이템 피드백", itemLevel.getContent().getFirst().content());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 비공개 전환 후 접근 제어
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void ownerCanAccessFeedbackAfterUnpublish() {
        feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("피드백", null, null, List.of()));
        tripService.unpublishTrip(owner.getId(), tripId);

        // 소유자는 비공개 이후에도 열람 가능
        Page<FeedbackResponse> page = feedbackService.getList(
                tripId, null, null, owner.getId(), DEFAULT_PAGE);

        assertEquals(1, page.getTotalElements());
    }

    @Test
    void feedbackAuthorCanAccessFeedbackAfterUnpublish() {
        feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("피드백", null, null, List.of()));
        tripService.unpublishTrip(owner.getId(), tripId);

        // 피드백 작성자도 열람 가능
        Page<FeedbackResponse> page = feedbackService.getList(
                tripId, null, null, reviewer.getId(), DEFAULT_PAGE);

        assertEquals(1, page.getTotalElements());
    }

    @Test
    void otherUserCannotAccessFeedbackAfterUnpublish() {
        feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("피드백", null, null, List.of()));
        tripService.unpublishTrip(owner.getId(), tripId);

        CustomException ex = assertThrows(CustomException.class, () ->
                feedbackService.getList(tripId, null, null, other.getId(), DEFAULT_PAGE));

        assertEquals(ErrorCode.TRIP_NOT_PUBLISHED, ex.getErrorCode());
    }

    @Test
    void unauthenticatedCannotAccessFeedbackAfterUnpublish() {
        feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("피드백", null, null, List.of()));
        tripService.unpublishTrip(owner.getId(), tripId);

        CustomException ex = assertThrows(CustomException.class, () ->
                feedbackService.getList(tripId, null, null, null, DEFAULT_PAGE));

        assertEquals(ErrorCode.TRIP_NOT_PUBLISHED, ex.getErrorCode());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 추천 장소 → 카트 담기 (Trip 소유자 단축 경로)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void ownerAddsRecommendedPlaceToCart() {
        when(tourService.getContentDetail("REC001")).thenReturn(stubContent("REC001", "추천 장소"));

        FeedbackResponse feedback = feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("여기 가보세요", null, null,
                        List.of(new RecommendationRequest("REC001"))));
        UUID recommendationId = feedback.recommendations().getFirst().id();

        entityManager.flush();
        entityManager.clear();

        feedbackService.addRecommendationToCart(owner.getId(), tripId, recommendationId);

        boolean saved = cartItemRepository.existsByUserIdAndTourApiContentId(owner.getId(), "REC001");
        assertTrue(saved);
    }

    @Test
    void nonOwnerCannotAddRecommendationToCart() {
        when(tourService.getContentDetail("REC002")).thenReturn(stubContent("REC002", "추천 장소2"));

        FeedbackResponse feedback = feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("여기 가보세요", null, null,
                        List.of(new RecommendationRequest("REC002"))));
        UUID recommendationId = feedback.recommendations().getFirst().id();

        entityManager.flush();
        entityManager.clear();

        CustomException ex = assertThrows(CustomException.class, () ->
                feedbackService.addRecommendationToCart(reviewer.getId(), tripId, recommendationId));

        assertEquals(ErrorCode.TRIP_ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    void wrongTripIdRejectedWhenAddingRecommendationToCart() {
        when(tourService.getContentDetail("REC003")).thenReturn(stubContent("REC003", "추천 장소3"));

        FeedbackResponse feedback = feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("여기 가보세요", null, null,
                        List.of(new RecommendationRequest("REC003"))));
        UUID recommendationId = feedback.recommendations().getFirst().id();

        entityManager.flush();
        entityManager.clear();

        CustomException ex = assertThrows(CustomException.class, () ->
                feedbackService.addRecommendationToCart(owner.getId(), UUID.randomUUID(), recommendationId));

        assertEquals(ErrorCode.FEEDBACK_RECOMMENDATION_NOT_FOUND, ex.getErrorCode());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 연관 삭제
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void deletingTripItemNullsOutFeedbackReferenceButKeepsFeedback() {
        UUID feedbackId = feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("아이템 피드백", null, itemId, List.of())).id();

        entityManager.flush();
        entityManager.clear();

        // 공개 상태에서는 일차의 마지막 일정을 지울 수 없으므로 비공개로 돌린 뒤 삭제
        tripService.unpublishTrip(owner.getId(), tripId);
        UUID fetchedDayId = tripService.getTripDetail(owner.getId(), tripId).days().getFirst().id();
        tripService.deleteTripItem(owner.getId(), tripId, fetchedDayId, itemId);

        entityManager.flush();
        entityManager.clear();

        // 피드백은 남아 있되 trip_item_id가 null로 처리
        FeedbackResponse feedback = feedbackService.getList(
                tripId, null, null, owner.getId(), DEFAULT_PAGE)
                .getContent().stream()
                .filter(f -> f.id().equals(feedbackId))
                .findFirst().orElseThrow();

        assertNull(feedback.tripItemId());
        assertEquals("아이템 피드백", feedback.content());
    }

    @Test
    void updatingTripDatesPreservesFeedbackText() {
        UUID feedbackId = feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("날짜 변경 후에도 남아야 할 피드백", dayId, null, List.of())).id();

        entityManager.flush();
        entityManager.clear();

        // 공개 상태에서는 날짜를 바꿀 수 없으므로 비공개로 돌린 뒤 변경
        tripService.unpublishTrip(owner.getId(), tripId);
        // 날짜 변경 → 일차·아이템 초기화, 피드백 텍스트는 보존
        LocalDate newStart = LocalDate.of(2026, 8, 1);
        tripService.updateTrip(owner.getId(), tripId,
                new UpdateTripRequest("제목변경", newStart, newStart.plusDays(2)));

        entityManager.flush();
        entityManager.clear();

        // 피드백이 남아 있고 day 참조만 null
        Page<FeedbackResponse> all = feedbackService.getList(tripId, null, null, owner.getId(), DEFAULT_PAGE);
        FeedbackResponse feedback = all.getContent().stream()
                .filter(f -> f.id().equals(feedbackId))
                .findFirst().orElseThrow();

        assertEquals("날짜 변경 후에도 남아야 할 피드백", feedback.content());
        assertNull(feedback.tripDayId());
    }

    @Test
    void deletingTripCascadesFeedbackAndRecommendations() {
        when(tourService.getContentDetail("AAA")).thenReturn(stubContent("AAA", "장소A"));
        UUID feedbackId = feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("삭제될 피드백", null, null,
                        List.of(new RecommendationRequest("AAA")))).id();

        entityManager.flush();
        entityManager.clear();

        tripService.deleteTrip(owner.getId(), tripId);

        entityManager.flush();
        entityManager.clear();

        assertTrue(feedbackRepository.findById(feedbackId).isEmpty());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 참견 좋아요
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void likingFeedbackIncrementsCountAndMarksLikedByMe() {
        UUID feedbackId = feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("좋아요 받을 참견", null, null, List.of())).id();

        FeedbackResponse liked = feedbackService.likeFeedback(owner.getId(), tripId, feedbackId);

        assertEquals(1, liked.likeCount());
        assertTrue(liked.likedByMe());
    }

    @Test
    void likingTwiceIsRejected() {
        UUID feedbackId = feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("중복 좋아요 방지", null, null, List.of())).id();
        feedbackService.likeFeedback(owner.getId(), tripId, feedbackId);

        CustomException ex = assertThrows(CustomException.class, () ->
                feedbackService.likeFeedback(owner.getId(), tripId, feedbackId));

        assertEquals(ErrorCode.FEEDBACK_ALREADY_LIKED, ex.getErrorCode());
    }

    @Test
    void unlikingRemovesLikeAndDecrementsCount() {
        UUID feedbackId = feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("좋아요 취소 대상", null, null, List.of())).id();
        feedbackService.likeFeedback(owner.getId(), tripId, feedbackId);

        FeedbackResponse unliked = feedbackService.unlikeFeedback(owner.getId(), tripId, feedbackId);

        assertEquals(0, unliked.likeCount());
        assertFalse(unliked.likedByMe());
    }

    @Test
    void unlikingWithoutLikeIsRejected() {
        UUID feedbackId = feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("좋아요 없음", null, null, List.of())).id();

        CustomException ex = assertThrows(CustomException.class, () ->
                feedbackService.unlikeFeedback(owner.getId(), tripId, feedbackId));

        assertEquals(ErrorCode.FEEDBACK_LIKE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void feedbackListCarriesLikeCountAndLikedByMePerCaller() {
        UUID feedbackId = feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("목록에서 좋아요 확인", null, null, List.of())).id();
        feedbackService.likeFeedback(owner.getId(), tripId, feedbackId);

        FeedbackResponse asOwner = feedbackService.getList(tripId, null, null, owner.getId(), DEFAULT_PAGE)
                .getContent().stream().filter(f -> f.id().equals(feedbackId)).findFirst().orElseThrow();
        FeedbackResponse asOther = feedbackService.getList(tripId, null, null, other.getId(), DEFAULT_PAGE)
                .getContent().stream().filter(f -> f.id().equals(feedbackId)).findFirst().orElseThrow();

        assertEquals(1, asOwner.likeCount());
        assertTrue(asOwner.likedByMe());
        assertEquals(1, asOther.likeCount());
        assertFalse(asOther.likedByMe());
    }

    @Test
    void deletingTripCascadesFeedbackLikes() {
        UUID feedbackId = feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("좋아요 달린 채로 삭제될 참견", null, null, List.of())).id();
        feedbackService.likeFeedback(owner.getId(), tripId, feedbackId);

        entityManager.flush();
        entityManager.clear();

        tripService.deleteTrip(owner.getId(), tripId);

        entityManager.flush();
        entityManager.clear();

        assertTrue(feedbackLikeRepository.countGroupByFeedbackIds(List.of(feedbackId)).isEmpty());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────────────────

    private ContentDetail stubContent(String contentId, String title) {
        return new ContentDetail(contentId, "12", title, "주소", null,
                "32", "3", null, null, null, "img.jpg",
                128.9, 37.6, null, null, null, List.of(), null, null, null, null);
    }
}
