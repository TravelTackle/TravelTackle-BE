package Timeout.travel_tackle.trip;

import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.cart.repository.CartItemRepository;
import Timeout.travel_tackle.entity.CartItem;
import Timeout.travel_tackle.entity.Enum.FeedItemType;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import Timeout.travel_tackle.trip.dto.AddTripItemRequest;
import Timeout.travel_tackle.trip.dto.CreateTripRequest;
import Timeout.travel_tackle.trip.dto.SavedTripResponse;
import Timeout.travel_tackle.trip.dto.TripDetailResponse;
import Timeout.travel_tackle.trip.dto.TripItemResponse;
import Timeout.travel_tackle.trip.dto.TripRecordRequest;
import Timeout.travel_tackle.trip.dto.TripSummaryResponse;
import Timeout.travel_tackle.trip.service.SavedTripService;
import Timeout.travel_tackle.trip.service.TripRecordService;
import Timeout.travel_tackle.trip.service.TripService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class SavedTripServiceTests {

    @Autowired SavedTripService savedTripService;
    @Autowired TripService tripService;
    @Autowired TripRecordService tripRecordService;
    @Autowired UserRepository userRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired EntityManager entityManager;

    private User owner;
    private User viewer;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("owner@example.com", "주인", "KR"));
        viewer = userRepository.save(new User("viewer@example.com", "저장하는사람", "KR"));
    }

    @Test
    void savingOnlyScrapsAndDoesNotCopyYet() {
        UUID originalId = createPublishedTripWithItems(owner, "A", "B");

        SavedTripResponse scrap = savedTripService.save(viewer.getId(), originalId, FeedItemType.PLAN);

        assertEquals(originalId, scrap.originalTripId());
        assertEquals("주인", scrap.ownerName());
        assertNull(scrap.ownerProfileImageUrl());
        assertEquals(null, scrap.copiedTripId());

        List<SavedTripResponse> saved = savedTripService.getSavedTrips(viewer.getId());
        assertEquals(1, saved.size());
        assertEquals(originalId, saved.getFirst().originalTripId());
        assertEquals(null, saved.getFirst().copiedTripId());
    }

    @Test
    void copyingScrapedTripCreatesEditableCopyOwnedByViewer() {
        UUID originalId = createPublishedTripWithItems(owner, "A", "B");
        SavedTripResponse scrap = savedTripService.save(viewer.getId(), originalId, FeedItemType.PLAN);

        TripSummaryResponse copy = savedTripService.copy(viewer.getId(), scrap.savedTripId());

        assertNotEquals(originalId, copy.id());
        // 복사본은 viewer 소유이므로 viewer가 상세 조회 가능
        TripDetailResponse copied = tripService.getTripDetail(viewer.getId(), copy.id());
        assertEquals("원본 여행 (주인님의 계획)", copied.title());
        assertEquals(1, copied.days().size());
        assertEquals(
                List.of("A", "B"),
                copied.days().getFirst().items().stream()
                        .map(TripItemResponse::tourApiContentId).toList());

        SavedTripResponse afterCopy = savedTripService.getSavedTrips(viewer.getId()).getFirst();
        assertEquals(copy.id(), afterCopy.copiedTripId());
    }

    @Test
    void copyingTwiceReturnsSameCopyInsteadOfDuplicating() {
        UUID originalId = createPublishedTripWithItems(owner, "A");
        SavedTripResponse scrap = savedTripService.save(viewer.getId(), originalId, FeedItemType.PLAN);

        TripSummaryResponse firstCopy = savedTripService.copy(viewer.getId(), scrap.savedTripId());
        TripSummaryResponse secondCopy = savedTripService.copy(viewer.getId(), scrap.savedTripId());

        assertEquals(firstCopy.id(), secondCopy.id());
    }

    @Test
    void savingWithRecordSourceTypeReturnsRecordShapedCard() {
        UUID tripId = createPublishedTripWithItems(owner, "A");
        tripRecordService.createRecord(owner.getId(), tripId, new TripRecordRequest("여행 후기 제목", "후기 내용",
                List.of(new TripRecordRequest.PhotoEntry("https://cdn.test/p1.jpg", "캡션"))));

        SavedTripResponse scrap = savedTripService.save(viewer.getId(), tripId, FeedItemType.RECORD);

        assertEquals(FeedItemType.RECORD, scrap.sourceType());
        assertEquals("여행 후기 제목", scrap.title());
        assertEquals("후기 내용", scrap.content());
        assertEquals(null, scrap.days());
    }

    @Test
    void savingWithPlanSourceTypeReturnsPlanShapedCard() {
        UUID tripId = createPublishedTripWithItems(owner, "A");

        SavedTripResponse scrap = savedTripService.save(viewer.getId(), tripId, FeedItemType.PLAN);

        assertEquals(FeedItemType.PLAN, scrap.sourceType());
        assertEquals("원본 여행", scrap.title());
        assertEquals(null, scrap.content());
        assertEquals(1, scrap.days().size());
    }

    @Test
    void recordSourceTypeFallsBackToPlanShapeWhenRecordLaterDeleted() {
        UUID tripId = createPublishedTripWithItems(owner, "A");
        tripRecordService.createRecord(owner.getId(), tripId, new TripRecordRequest("여행 후기 제목", "후기 내용",
                List.of(new TripRecordRequest.PhotoEntry("https://cdn.test/p1.jpg", "캡션"))));
        SavedTripResponse scrap = savedTripService.save(viewer.getId(), tripId, FeedItemType.RECORD);
        assertEquals(FeedItemType.RECORD, scrap.sourceType());

        tripRecordService.deleteRecord(owner.getId(), tripId);
        entityManager.flush();
        entityManager.clear();

        SavedTripResponse afterDelete = savedTripService.getSavedTrips(viewer.getId()).getFirst();
        assertEquals(FeedItemType.PLAN, afterDelete.sourceType());
        assertEquals("원본 여행", afterDelete.title());
        assertEquals(null, afterDelete.content());
        assertEquals(1, afterDelete.days().size());
    }

    @Test
    void rescrapingAfterUnsaveUsesLatestSourceType() {
        UUID tripId = createPublishedTripWithItems(owner, "A");
        tripRecordService.createRecord(owner.getId(), tripId, new TripRecordRequest("여행 후기 제목", "후기 내용",
                List.of(new TripRecordRequest.PhotoEntry("https://cdn.test/p1.jpg", "캡션"))));

        SavedTripResponse firstScrap = savedTripService.save(viewer.getId(), tripId, FeedItemType.PLAN);
        assertEquals(FeedItemType.PLAN, firstScrap.sourceType());
        savedTripService.unsave(viewer.getId(), firstScrap.savedTripId());

        SavedTripResponse secondScrap = savedTripService.save(viewer.getId(), tripId, FeedItemType.RECORD);

        assertEquals(FeedItemType.RECORD, secondScrap.sourceType());
    }

    @Test
    void rejectsSavingUnpublishedTrip() {
        UUID tripId = tripService.createTrip(owner.getId(),
                new CreateTripRequest("비공개 여행", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1))).id();

        CustomException exception = assertThrows(CustomException.class,
                () -> savedTripService.save(viewer.getId(), tripId, FeedItemType.PLAN));

        assertEquals(ErrorCode.TRIP_NOT_PUBLISHED, exception.getErrorCode());
    }

    @Test
    void rejectsSavingOwnTrip() {
        UUID tripId = createPublishedTripWithItems(owner, "A");

        CustomException exception = assertThrows(CustomException.class,
                () -> savedTripService.save(owner.getId(), tripId, FeedItemType.PLAN));

        assertEquals(ErrorCode.CANNOT_SAVE_OWN_TRIP, exception.getErrorCode());
    }

    @Test
    void rejectsSavingSameTripTwice() {
        UUID tripId = createPublishedTripWithItems(owner, "A");
        savedTripService.save(viewer.getId(), tripId, FeedItemType.PLAN);

        CustomException exception = assertThrows(CustomException.class,
                () -> savedTripService.save(viewer.getId(), tripId, FeedItemType.PLAN));

        assertEquals(ErrorCode.TRIP_ALREADY_SAVED, exception.getErrorCode());
    }

    @Test
    void unsaveRemovesLedgerButKeepsCopy() {
        UUID tripId = createPublishedTripWithItems(owner, "A");
        SavedTripResponse scrap = savedTripService.save(viewer.getId(), tripId, FeedItemType.PLAN);
        TripSummaryResponse copy = savedTripService.copy(viewer.getId(), scrap.savedTripId());

        savedTripService.unsave(viewer.getId(), scrap.savedTripId());

        assertTrue(savedTripService.getSavedTrips(viewer.getId()).isEmpty());
        // 복사본은 viewer 소유로 그대로 남아 있어야 한다
        assertEquals(copy.id(), tripService.getTripDetail(viewer.getId(), copy.id()).id());
    }

    @Test
    void deletingTripWithRecordAndSavedReferencesSucceeds() {
        UUID tripId = createPublishedTripWithItems(owner, "A");
        tripRecordService.createRecord(owner.getId(), tripId, new TripRecordRequest("여행 제목", "후기 내용",
                List.of(new TripRecordRequest.PhotoEntry("https://cdn.test/p1.jpg", "캡션"))));
        SavedTripResponse scrap = savedTripService.save(viewer.getId(), tripId, FeedItemType.PLAN);
        TripSummaryResponse copy = savedTripService.copy(viewer.getId(), scrap.savedTripId());

        // 운영에선 요청마다 영속성 컨텍스트가 새로 열린다. 같은 트랜잭션으로 묶인 테스트가
        // 이를 반영하도록 셋업에서 로드된 엔티티를 분리한 뒤 삭제를 호출한다.
        entityManager.flush();
        entityManager.clear();

        // trip_photos / saved_trips FK 제약 위반 없이 삭제되어야 한다
        tripService.deleteTrip(owner.getId(), tripId);

        // 저장 이력은 정리되고, viewer의 복사본은 독립적으로 남는다
        assertTrue(savedTripService.getSavedTrips(viewer.getId()).isEmpty());
        assertEquals(copy.id(), tripService.getTripDetail(viewer.getId(), copy.id()).id());
    }

    @Test
    void deletingCopiedTripClearsReferenceButKeepsScrapHistory() {
        UUID tripId = createPublishedTripWithItems(owner, "A");
        SavedTripResponse scrap = savedTripService.save(viewer.getId(), tripId, FeedItemType.PLAN);
        TripSummaryResponse copy = savedTripService.copy(viewer.getId(), scrap.savedTripId());

        entityManager.flush();
        entityManager.clear();

        // viewer가 자신의 복사본을 지워도 원본 스크랩 이력의 FK 제약에 걸리면 안 된다
        tripService.deleteTrip(viewer.getId(), copy.id());

        SavedTripResponse afterDelete = savedTripService.getSavedTrips(viewer.getId()).getFirst();
        assertEquals(tripId, afterDelete.originalTripId());
        assertEquals(null, afterDelete.copiedTripId());
    }

    private UUID createPublishedTripWithItems(User tripOwner, String... contentIds) {
        UUID ownerId = tripOwner.getId();
        LocalDate date = LocalDate.of(2026, 7, 1);
        UUID tripId = tripService.createTrip(ownerId,
                new CreateTripRequest("원본 여행", date, date)).id();
        UUID dayId = tripService.getTripDetail(ownerId, tripId).days().getFirst().id();

        for (String contentId : contentIds) {
            CartItem cartItem = cartItemRepository.save(
                    new CartItem(tripOwner, contentId, contentId, null, "1", null, null, null, null, null));
            tripService.addTripItem(ownerId, tripId, dayId,
                    new AddTripItemRequest(cartItem.getId(), null, null));
        }
        tripService.publishTrip(ownerId, tripId, null);
        return tripId;
    }
}
