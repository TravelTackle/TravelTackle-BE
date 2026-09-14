package Timeout.travel_tackle.trip;

import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.cart.repository.CartItemRepository;
import Timeout.travel_tackle.entity.CartItem;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import Timeout.travel_tackle.trip.dto.AddTripItemRequest;
import Timeout.travel_tackle.trip.dto.CreateTripRequest;
import Timeout.travel_tackle.trip.dto.MoveTripItemRequest;
import Timeout.travel_tackle.trip.dto.ReorderTripItemsRequest;
import Timeout.travel_tackle.trip.dto.TripDayResponse;
import Timeout.travel_tackle.trip.dto.TripDetailResponse;
import Timeout.travel_tackle.trip.dto.UpdateTripRequest;
import Timeout.travel_tackle.trip.dto.TripItemResponse;
import Timeout.travel_tackle.trip.dto.UpdateTripItemRequest;
import Timeout.travel_tackle.trip.service.TripService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class TripServiceTests {

    @Autowired TripService tripService;
    @Autowired UserRepository userRepository;
    @Autowired CartItemRepository cartItemRepository;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("trip-test@example.com", "여행자", "KR"));
        userId = user.getId();
    }

    @Test
    void addsOwnedCartItemToTripUsingSavedSnapshot() {
        TripDetailResponse trip = createTripWithDays(1);
        TripDayResponse day = trip.days().getFirst();
        CartItem cartItem = saveCartItem(user, "125266", "경복궁", "image.jpg");

        TripItemResponse result = tripService.addTripItem(userId, trip.id(), day.id(),
                new AddTripItemRequest(cartItem.getId(), null, null));

        assertEquals("125266", result.tourApiContentId());
        assertEquals("경복궁", result.cachedTitle());
        assertEquals("image.jpg", result.cachedImageUrl());
    }

    @Test
    void rejectsAnotherUsersCartItem() {
        TripDetailResponse trip = createTripWithDays(1);
        TripDayResponse day = trip.days().getFirst();
        User anotherUser = userRepository.save(new User("other@example.com", "다른 사용자", "KR"));
        CartItem cartItem = saveCartItem(anotherUser, "999", "다른 장바구니", null);

        CustomException exception = assertThrows(CustomException.class, () ->
                tripService.addTripItem(userId, trip.id(), day.id(),
                        new AddTripItemRequest(cartItem.getId(), null, null)));

        assertEquals(ErrorCode.CART_ITEM_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void rejectsPartialReorderRequest() {
        TripDetailResponse trip = createTripWithDays(1);
        TripDayResponse day = trip.days().getFirst();
        TripItemResponse first = addItem(trip.id(), day.id(), "1");
        addItem(trip.id(), day.id(), "2");

        CustomException exception = assertThrows(CustomException.class, () ->
                tripService.reorderTripItems(userId, trip.id(), day.id(),
                        new ReorderTripItemsRequest(List.of(first.id()))));

        assertEquals(ErrorCode.INVALID_TRIP_ITEM_ORDER, exception.getErrorCode());
    }

    @Test
    void movesItemIntoOccupiedIndexAndNormalizesBothDays() {
        TripDetailResponse trip = createTripWithDays(2);
        TripDayResponse firstDay = trip.days().get(0);
        TripDayResponse secondDay = trip.days().get(1);
        addItem(trip.id(), firstDay.id(), "A");
        TripItemResponse movingItem = addItem(trip.id(), firstDay.id(), "B");
        addItem(trip.id(), secondDay.id(), "C");
        addItem(trip.id(), secondDay.id(), "D");

        tripService.moveTripItem(userId, trip.id(), movingItem.id(),
                new MoveTripItemRequest(secondDay.id(), 0));

        TripDetailResponse result = tripService.getTripDetail(userId, trip.id());
        assertItems(result.days().get(0).items(), List.of("A"));
        assertItems(result.days().get(1).items(), List.of("B", "C", "D"));
    }

    @Test
    void timeOnlyUpdateDoesNotWipeExistingMemo() {
        TripDetailResponse trip = createTripWithDays(1);
        TripDayResponse day = trip.days().getFirst();
        TripItemResponse item = addItem(trip.id(), day.id(), "1");

        tripService.updateTripItem(userId, trip.id(), day.id(), item.id(),
                new UpdateTripItemRequest(null, null, "여기 꼭 가기"));

        TripItemResponse afterTimeOnlyUpdate = tripService.updateTripItem(userId, trip.id(), day.id(), item.id(),
                new UpdateTripItemRequest(java.time.LocalTime.of(10, 0), java.time.LocalTime.of(11, 0), null));

        assertEquals("여기 꼭 가기", afterTimeOnlyUpdate.memo());
    }

    @Test
    void explicitEmptyMemoClearsIt() {
        TripDetailResponse trip = createTripWithDays(1);
        TripDayResponse day = trip.days().getFirst();
        TripItemResponse item = addItem(trip.id(), day.id(), "1");

        tripService.updateTripItem(userId, trip.id(), day.id(), item.id(),
                new UpdateTripItemRequest(null, null, "여기 꼭 가기"));

        TripItemResponse cleared = tripService.updateTripItem(userId, trip.id(), day.id(), item.id(),
                new UpdateTripItemRequest(null, null, ""));

        assertEquals("", cleared.memo());
    }


    // --- 공개 조건: 모든 일차에 일정 1개 이상 ---

    @Test
    void publishRejectsTripWithEmptyDaysAndListsThem() {
        TripDetailResponse trip = createTripWithDays(3);
        addItem(trip.id(), trip.days().get(0).id(), "1"); // Day 1 만 채움

        CustomException ex = assertThrows(CustomException.class, () -> tripService.publishTrip(userId, trip.id()));

        assertEquals(ErrorCode.TRIP_PUBLISH_REQUIRES_ITEMS, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Day 2, Day 3"), ex.getMessage());
        assertFalse(tripService.getTripDetail(userId, trip.id()).published());
    }

    @Test
    void publishSucceedsWhenEveryDayHasAnItem() {
        TripDetailResponse trip = createTripWithDays(2);
        addItem(trip.id(), trip.days().get(0).id(), "1");
        addItem(trip.id(), trip.days().get(1).id(), "2");

        tripService.publishTrip(userId, trip.id());

        assertTrue(tripService.getTripDetail(userId, trip.id()).published());
    }

    @Test
    void publishedTripKeepsAtLeastOneItemPerDayOnDelete() {
        TripDetailResponse trip = createTripWithDays(1);
        UUID dayId = trip.days().getFirst().id();
        TripItemResponse first = addItem(trip.id(), dayId, "1");
        TripItemResponse second = addItem(trip.id(), dayId, "2");
        tripService.publishTrip(userId, trip.id());

        tripService.deleteTripItem(userId, trip.id(), dayId, second.id()); // 2개 -> 1개는 허용

        CustomException ex = assertThrows(CustomException.class, () ->
                tripService.deleteTripItem(userId, trip.id(), dayId, first.id()));
        assertEquals(ErrorCode.PUBLISHED_TRIP_DAY_MUST_KEEP_ITEM, ex.getErrorCode());

        // 비공개로 돌리면 마지막 일정도 지울 수 있다
        tripService.unpublishTrip(userId, trip.id());
        tripService.deleteTripItem(userId, trip.id(), dayId, first.id());
        assertTrue(tripService.getTripDetail(userId, trip.id()).days().getFirst().items().isEmpty());
    }

    @Test
    void publishedTripRejectsMovingLastItemOutOfADay() {
        TripDetailResponse trip = createTripWithDays(2);
        UUID day1 = trip.days().get(0).id();
        UUID day2 = trip.days().get(1).id();
        TripItemResponse only = addItem(trip.id(), day1, "1");
        addItem(trip.id(), day2, "2");
        tripService.publishTrip(userId, trip.id());

        CustomException ex = assertThrows(CustomException.class, () ->
                tripService.moveTripItem(userId, trip.id(), only.id(), new MoveTripItemRequest(day2, null)));
        assertEquals(ErrorCode.PUBLISHED_TRIP_DAY_MUST_KEEP_ITEM, ex.getErrorCode());

        // 같은 날 안에서 순서만 바꾸는 건 허용
        tripService.moveTripItem(userId, trip.id(), only.id(), new MoveTripItemRequest(day1, 0));
    }

    @Test
    void publishedTripRejectsDateChangeButAllowsTitleChange() {
        TripDetailResponse trip = createTripWithDays(1);
        addItem(trip.id(), trip.days().getFirst().id(), "1");
        tripService.publishTrip(userId, trip.id());
        LocalDate start = LocalDate.of(2026, 7, 1);

        tripService.updateTrip(userId, trip.id(), new UpdateTripRequest("새 제목", start, start));
        assertEquals("새 제목", tripService.getTripDetail(userId, trip.id()).title());

        CustomException ex = assertThrows(CustomException.class, () ->
                tripService.updateTrip(userId, trip.id(), new UpdateTripRequest("새 제목", start, start.plusDays(1))));
        assertEquals(ErrorCode.PUBLISHED_TRIP_DATES_LOCKED, ex.getErrorCode());
    }

    private TripDetailResponse createTripWithDays(int dayCount) {
        LocalDate startDate = LocalDate.of(2026, 7, 1);
        UUID tripId = tripService.createTrip(userId,
                new CreateTripRequest("테스트 여행", startDate,
                        startDate.plusDays(dayCount - 1))).id();
        return tripService.getTripDetail(userId, tripId);
    }

    private TripItemResponse addItem(UUID tripId, UUID dayId, String contentId) {
        CartItem cartItem = saveCartItem(user, contentId, contentId, null);
        return tripService.addTripItem(userId, tripId, dayId,
                new AddTripItemRequest(cartItem.getId(), null, null));
    }

    private CartItem saveCartItem(User owner, String contentId, String title, String imageUrl) {
        return cartItemRepository.save(new CartItem(owner, contentId, "KorService2", title, imageUrl, "1", null, null, null, null));
    }

    private void assertItems(List<TripItemResponse> items, List<String> expectedIds) {
        assertEquals(expectedIds, items.stream().map(TripItemResponse::tourApiContentId).toList());
        assertEquals(
                java.util.stream.IntStream.range(0, items.size()).boxed().toList(),
                items.stream().map(TripItemResponse::orderIndex).toList()
        );
    }
}
