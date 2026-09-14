package Timeout.travel_tackle.trip;

import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.cart.repository.CartItemRepository;
import Timeout.travel_tackle.entity.CartItem;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import Timeout.travel_tackle.trip.dto.AddTripItemRequest;
import Timeout.travel_tackle.trip.dto.CreateFeedbackRequest;
import Timeout.travel_tackle.trip.dto.CreateTripRequest;
import Timeout.travel_tackle.entity.Enum.FeedItemType;
import Timeout.travel_tackle.trip.dto.FeedItemResponse;
import Timeout.travel_tackle.trip.dto.FeedSort;
import Timeout.travel_tackle.trip.dto.RegionCountResponse;
import Timeout.travel_tackle.trip.dto.TripDetailResponse;
import Timeout.travel_tackle.trip.dto.TripRecordRequest;
import Timeout.travel_tackle.trip.service.FeedService;
import Timeout.travel_tackle.trip.service.SavedTripService;
import Timeout.travel_tackle.trip.service.TripFeedbackService;
import Timeout.travel_tackle.trip.service.TripRecordService;
import Timeout.travel_tackle.trip.service.TripService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class FeedServiceTests {

    @Autowired FeedService feedService;
    @Autowired TripService tripService;
    @Autowired TripFeedbackService feedbackService;
    @Autowired TripRecordService tripRecordService;
    @Autowired SavedTripService savedTripService;
    @Autowired UserRepository userRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired EntityManager entityManager;

    private User owner;
    private User reviewerA;
    private User reviewerB;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("owner@feed.test", "계획자", "KR"));
        reviewerA = userRepository.save(new User("a@feed.test", "리뷰어A", "KR"));
        reviewerB = userRepository.save(new User("b@feed.test", "리뷰어B", "KR"));
    }

    @Test
    void popularSortOrdersByFeedbackPlusSaveCountThenCreatedAtDesc() {
        UUID oldest = createPublishedTrip("첫 번째");    // 참견 1 + 스크랩 0 = 1
        UUID middle = createPublishedTrip("두 번째");    // 참견 0 + 스크랩 0 = 0
        UUID newest = createPublishedTrip("세 번째");    // 참견 0 + 스크랩 0 = 0 — middle 과 동점이면 최신이 앞
        UUID scrapped = createPublishedTrip("네 번째");  // 참견 0 + 스크랩 2 = 2 — 참견 없이 스크랩만으로도 올라간다
        UUID popular = createPublishedTrip("다섯 번째"); // 참견 2 + 스크랩 1 = 3

        giveFeedback(reviewerA, oldest);
        giveFeedback(reviewerA, popular);
        giveFeedback(reviewerB, popular);
        savedTripService.save(reviewerA.getId(), popular, FeedItemType.PLAN);
        savedTripService.save(reviewerA.getId(), scrapped, FeedItemType.PLAN);
        savedTripService.save(reviewerB.getId(), scrapped, FeedItemType.PLAN);
        entityManager.flush();
        entityManager.clear();

        List<UUID> order = feedService.getFeed(PageRequest.of(0, 10), FeedSort.POPULAR)
                .getContent().stream().map(FeedItemResponse::tripId).toList();

        // 저장 복사본은 비공개라 피드에 안 나온다
        assertEquals(List.of(popular, scrapped, oldest, newest, middle), order);
    }

    @Test
    void feedItemsAndPublicDetailCarryOwnerId() {
        UUID tripId = createPublishedTrip("소유자 확인");
        entityManager.flush();
        entityManager.clear();

        FeedItemResponse item = feedService.getFeed(PageRequest.of(0, 10), FeedSort.LATEST).getContent().getFirst();
        assertEquals(owner.getId(), item.ownerId());
        assertEquals("계획자", item.ownerName());

        assertEquals(owner.getId(), feedService.getPublicTripDetail(tripId, null).ownerId());
    }

    @Test
    void regionCountsCountEveryRegionOfATripOnceWithinPeriod() {
        LocalDateTime june = LocalDateTime.of(2026, 6, 15, 12, 0);
        LocalDateTime july = LocalDateTime.of(2026, 7, 10, 12, 0);
        createPublishedTripInRegion("서울1", "서울특별시 종로구 사직로 161", july);
        createPublishedTripInRegion("서울2", "서울특별시 용산구 남산공원길 105", july);
        createPublishedTripInRegion("제주", "제주특별자치도 제주시 우도면 우도해안길 32", july);
        createPublishedTripInRegion("지난달 제주", "제주특별자치도 제주시 애월읍 애월북서길 56", june);
        UUID unpublished = createPublishedTripInRegion("비공개 서울", "서울특별시 마포구 양화로 45", july);
        tripService.unpublishTrip(owner.getId(), unpublished);
        // 용인 1개 + 수원 2개가 섞인 계획: 용인 +1, 수원 +1 (같은 지역은 계획당 1번만)
        UUID mixed = createPublishedTripInRegion("용인·수원", "경기도 용인시 처인구 포곡읍 에버랜드로 199", july);
        addItemWithAddress(mixed, "경기도 수원시 팔달구 정조로 825");
        addItemWithAddress(mixed, "경기도 수원시 영통구 광교로 145");
        // 첫 일정에 주소가 없어도 다른 일정의 지역은 센다
        UUID noAddressFirst = createPublishedTripInRegion("주소 없는 첫 일정", null, july);
        addItemWithAddress(noAddressFirst, "서울특별시 강남구 테헤란로 1");
        entityManager.flush();
        entityManager.clear();

        List<RegionCountResponse> thisMonth = feedService.getRegionCounts(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 10);
        assertEquals(List.of("서울", "수원", "용인", "제주"),
                thisMonth.stream().map(RegionCountResponse::region).toList()); // 동점(1)은 지역명 오름차순
        assertEquals(List.of(3L, 1L, 1L, 1L),
                thisMonth.stream().map(RegionCountResponse::tripCount).toList());

        List<RegionCountResponse> all = feedService.getRegionCounts(null, null, 10);
        assertEquals(2L, all.stream().filter(r -> r.region().equals("제주")).findFirst().orElseThrow().tripCount());

        assertEquals(1, feedService.getRegionCounts(null, null, 1).size());
        assertTrue(feedService.getRegionCounts(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 10).isEmpty());
    }

    @Test
    void regionCountsRejectReversedPeriod() {
        CustomException ex = assertThrows(CustomException.class, () ->
                feedService.getRegionCounts(LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 1), 10));
        assertEquals(ErrorCode.INVALID_INPUT, ex.getErrorCode());
    }

    @Test
    void latestSortKeepsCreatedAtDescOrder() {
        UUID first = createPublishedTrip("첫 번째");
        UUID second = createPublishedTrip("두 번째");
        giveFeedback(reviewerA, first);
        entityManager.flush();
        entityManager.clear();

        Page<FeedItemResponse> feed = feedService.getFeed(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")), FeedSort.LATEST);

        assertEquals(List.of(second, first),
                feed.getContent().stream().map(FeedItemResponse::tripId).toList());
    }

    @Test
    void feedItemsCarrySaveCountForPlanAndRecordCards() {
        UUID tripId = createPublishedTrip("저장되는 여행");
        tripRecordService.createRecord(owner.getId(), tripId,
                new TripRecordRequest("기록", "후기",
                        List.of(new TripRecordRequest.PhotoEntry("https://cdn.test/p1.jpg", "캡션"))));
        savedTripService.save(reviewerA.getId(), tripId, FeedItemType.PLAN);
        savedTripService.save(reviewerB.getId(), tripId, FeedItemType.PLAN);
        entityManager.flush();
        entityManager.clear();

        List<FeedItemResponse> items = feedService.getFeed(PageRequest.of(0, 10), FeedSort.POPULAR).getContent();

        assertEquals(2, items.size());
        assertEquals(FeedItemType.PLAN, items.get(0).type());
        assertEquals(2, items.get(0).saveCount());
        assertEquals(FeedItemType.RECORD, items.get(1).type());
        assertEquals(2, items.get(1).saveCount());
    }

    @Test
    void unknownSortValueIsRejected() {
        CustomException ex = assertThrows(CustomException.class, () -> FeedSort.from("trending"));
        assertEquals(ErrorCode.INVALID_INPUT, ex.getErrorCode());
        assertEquals(FeedSort.POPULAR, FeedSort.from("popular"));
        assertEquals(FeedSort.LATEST, FeedSort.from("LATEST"));
        assertEquals(FeedSort.OLDEST, FeedSort.from("oldest"));
        assertEquals(FeedSort.RELEVANCE, FeedSort.from("relevance"));
    }

    @Test
    void keywordSearchMatchesTripTitleOrRecordContent() {
        UUID titleMatch = createPublishedTrip("부산 여행");
        UUID contentOnlyMatch = createPublishedTrip("아무 여행");
        tripRecordService.createRecord(owner.getId(), contentOnlyMatch,
                new TripRecordRequest("기록", "부산 맛집 다녀왔어요",
                        List.of(new TripRecordRequest.PhotoEntry("https://cdn.test/p1.jpg", "캡션"))));
        createPublishedTrip("서울 여행"); // 매칭 안 됨

        entityManager.flush();
        entityManager.clear();

        List<UUID> matchedTripIds = feedService.getFeed(PageRequest.of(0, 10), FeedSort.RELEVANCE, "부산")
                .getContent().stream().map(FeedItemResponse::tripId).distinct().toList();

        assertEquals(List.of(titleMatch, contentOnlyMatch), matchedTripIds);
    }

    @Test
    void relevanceSortRanksTitleMatchAboveContentOnlyMatch() {
        UUID contentOnlyMatch = createPublishedTrip("아무 여행");
        tripRecordService.createRecord(owner.getId(), contentOnlyMatch,
                new TripRecordRequest("기록", "제주 맛집 다녀왔어요",
                        List.of(new TripRecordRequest.PhotoEntry("https://cdn.test/p1.jpg", "캡션"))));
        UUID titleMatch = createPublishedTrip("제주 여행");

        entityManager.flush();
        entityManager.clear();

        List<UUID> order = feedService.getFeed(PageRequest.of(0, 10), FeedSort.RELEVANCE, "제주")
                .getContent().stream().map(FeedItemResponse::tripId).distinct().toList();

        assertEquals(List.of(titleMatch, contentOnlyMatch), order);
    }

    @Test
    void blankKeywordWithRelevanceSortFallsBackToPageableOrder() {
        // 컨트롤러가 keyword 없는 relevance 요청을 latest로 정규화해 정렬된 Pageable을 넘기는 것과 동일한 상황을 재현
        UUID first = createPublishedTrip("첫 번째");
        UUID second = createPublishedTrip("두 번째");
        entityManager.flush();
        entityManager.clear();

        List<UUID> order = feedService.getFeed(
                        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")), FeedSort.RELEVANCE, "  ")
                .getContent().stream().map(FeedItemResponse::tripId).distinct().toList();

        assertEquals(List.of(second, first), order);
    }

    @Test
    void itemTitleMatchRanksBelowTripAndRecordMatch() {
        UUID itemOnlyMatch = createPublishedTripWithItem("아무 여행", "해운대 해수욕장");
        UUID recordMatch = createPublishedTrip("아무 여행2");
        tripRecordService.createRecord(owner.getId(), recordMatch,
                new TripRecordRequest("기록", "해운대에서 놀았어요",
                        List.of(new TripRecordRequest.PhotoEntry("https://cdn.test/p1.jpg", "캡션"))));
        UUID titleMatch = createPublishedTrip("해운대 여행");

        entityManager.flush();
        entityManager.clear();

        List<UUID> order = feedService.getFeed(PageRequest.of(0, 10), FeedSort.RELEVANCE, "해운대")
                .getContent().stream().map(FeedItemResponse::tripId).distinct().toList();

        assertEquals(List.of(titleMatch, recordMatch, itemOnlyMatch), order);
    }

    @Test
    void oldestSortIsAscendingByCreatedAt() {
        UUID first = createPublishedTrip("첫 번째");
        UUID second = createPublishedTrip("두 번째");
        entityManager.flush();
        entityManager.clear();

        Page<FeedItemResponse> feed = feedService.getFeed(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "createdAt")), FeedSort.OLDEST);

        assertEquals(List.of(first, second),
                feed.getContent().stream().map(FeedItemResponse::tripId).toList());
    }

    private int createdSeq = 0;

    // @CreationTimestamp 는 연속 생성 시 같은 값이 될 수 있어, 동점 정렬(최신순) 검증이 흔들리지 않게 생성 시각을 명시한다
    private UUID createPublishedTrip(String title) {
        LocalDate date = LocalDate.of(2026, 7, 1);
        UUID tripId = tripService.createTrip(owner.getId(), new CreateTripRequest(title, date, date)).id();
        // 모든 일차에 일정이 있어야 공개할 수 있다
        UUID dayId = tripService.getTripDetail(owner.getId(), tripId).days().getFirst().id();
        CartItem cartItem = cartItemRepository.save(
                new CartItem(owner, "item-" + title, "KorService2", title, null, "1", null, null, null, null));
        tripService.addTripItem(owner.getId(), tripId, dayId, new AddTripItemRequest(cartItem.getId(), null, null));
        tripService.publishTrip(owner.getId(), tripId);
        entityManager.flush();
        entityManager.createNativeQuery("update trips set created_at = ? where id = ?")
                .setParameter(1, LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(++createdSeq))
                .setParameter(2, tripId)
                .executeUpdate();
        return tripId;
    }

    private UUID createPublishedTripWithItem(String title, String placeName) {
        LocalDate date = LocalDate.of(2026, 7, 1);
        UUID tripId = tripService.createTrip(owner.getId(), new CreateTripRequest(title, date, date)).id();
        TripDetailResponse detail = tripService.getTripDetail(owner.getId(), tripId);
        UUID dayId = detail.days().getFirst().id();
        CartItem cartItem = cartItemRepository.save(
                new CartItem(owner, "item-" + placeName, "KorService2", placeName, null, "1", null, null, null, null));
        tripService.addTripItem(owner.getId(), tripId, dayId,
                new AddTripItemRequest(cartItem.getId(), null, null));
        tripService.publishTrip(owner.getId(), tripId);
        entityManager.flush();
        return tripId;
    }


    // 첫 일정 주소와 생성 시각을 지정한 공개 계획. 주소는 TourAPI 없이 직접 채운다
    private UUID createPublishedTripInRegion(String title, String address, LocalDateTime createdAt) {
        LocalDate date = LocalDate.of(2026, 7, 1);
        UUID tripId = tripService.createTrip(owner.getId(), new CreateTripRequest(title, date, date)).id();
        UUID dayId = tripService.getTripDetail(owner.getId(), tripId).days().getFirst().id();
        CartItem cartItem = cartItemRepository.save(
                new CartItem(owner, "item-" + title, "KorService2", title, null, "1", null, null, null, null));
        UUID itemId = tripService.addTripItem(owner.getId(), tripId, dayId,
                new AddTripItemRequest(cartItem.getId(), null, null)).id();
        tripService.publishTrip(owner.getId(), tripId);
        entityManager.flush();
        entityManager.createNativeQuery("update trip_items set address = ? where id = ?")
                .setParameter(1, address).setParameter(2, itemId).executeUpdate();
        entityManager.createNativeQuery("update trips set created_at = ? where id = ?")
                .setParameter(1, createdAt).setParameter(2, tripId).executeUpdate();
        return tripId;
    }


    private void addItemWithAddress(UUID tripId, String address) {
        UUID dayId = tripService.getTripDetail(owner.getId(), tripId).days().getFirst().id();
        CartItem cartItem = cartItemRepository.save(
                new CartItem(owner, "item-" + UUID.randomUUID(), "KorService2", "장소", null, "1", null, null, null, null));
        UUID itemId = tripService.addTripItem(owner.getId(), tripId, dayId,
                new AddTripItemRequest(cartItem.getId(), null, null)).id();
        entityManager.flush();
        entityManager.createNativeQuery("update trip_items set address = ? where id = ?")
                .setParameter(1, address).setParameter(2, itemId).executeUpdate();
    }

    private void giveFeedback(User reviewer, UUID tripId) {
        feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("참견합니다", null, null, List.of()));
    }
}
