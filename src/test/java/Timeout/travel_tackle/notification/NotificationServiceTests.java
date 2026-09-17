package Timeout.travel_tackle.notification;

import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.cart.repository.CartItemRepository;
import Timeout.travel_tackle.entity.CartItem;
import Timeout.travel_tackle.entity.Enum.NotificationTarget;
import Timeout.travel_tackle.entity.Enum.NotificationType;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import Timeout.travel_tackle.notification.dto.NotificationPageResponse;
import Timeout.travel_tackle.notification.dto.NotificationPushEvent;
import Timeout.travel_tackle.notification.dto.ScrapNotificationCommand;
import Timeout.travel_tackle.notification.dto.UnreadCountResponse;
import Timeout.travel_tackle.notification.dto.NotificationResponse;
import Timeout.travel_tackle.notification.repository.NotificationRepository;
import Timeout.travel_tackle.notification.service.NotificationService;
import Timeout.travel_tackle.notification.sse.NotificationSseRegistry;
import Timeout.travel_tackle.trip.dto.AddTripItemRequest;
import Timeout.travel_tackle.trip.dto.CreateFeedbackRequest;
import Timeout.travel_tackle.trip.dto.CreateTripRequest;
import Timeout.travel_tackle.trip.dto.TripDetailResponse;
import Timeout.travel_tackle.entity.Enum.FeedItemType;
import Timeout.travel_tackle.trip.service.SavedTripService;
import Timeout.travel_tackle.trip.service.TripFeedbackService;
import Timeout.travel_tackle.trip.service.TripService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional
class NotificationServiceTests {

    @Autowired NotificationService notificationService;
    @Autowired NotificationRepository notificationRepository;
    @Autowired TripFeedbackService feedbackService;
    @Autowired SavedTripService savedTripService;
    @Autowired TripService tripService;
    @Autowired UserRepository userRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired EntityManager entityManager;

    @MockitoBean NotificationSseRegistry sseRegistry;

    private User owner;
    private User reviewer;
    private UUID tripId;
    private UUID dayId;
    private UUID itemId;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("owner-" + UUID.randomUUID() + "@noti.test", "계획자", "KR"));
        reviewer = userRepository.save(new User("reviewer-" + UUID.randomUUID() + "@noti.test", "리뷰어", "KR"));
        LocalDate date = LocalDate.of(2026, 7, 1);
        tripId = tripService.createTrip(owner.getId(), new CreateTripRequest("강릉 여행", date, date.plusDays(1))).id();
        TripDetailResponse detail = tripService.getTripDetail(owner.getId(), tripId);
        dayId = detail.days().get(0).id();
        itemId = addItem(dayId, "125266", "경포대");
        addItem(detail.days().get(1).id(), "125267", "정동진");
        tripService.publishTrip(owner.getId(), tripId, null);
    }

    @Test
    void feedbackOnItemCreatesNotificationWithFullContext() {
        feedbackService.create(reviewer.getId(), tripId,
                new CreateFeedbackRequest("경포대 갈 때 편한 신발 필수예요!", null, itemId, List.of()));

        NotificationPageResponse page = notificationService.getNotifications(owner.getId(), PageRequest.of(0, 10));
        assertEquals(1, page.unreadCount());
        NotificationResponse n = page.content().getFirst();
        assertEquals(NotificationType.FEEDBACK, n.type());
        assertFalse(n.read());
        assertEquals(reviewer.getId(), n.actor().id());
        assertEquals("리뷰어", n.actor().name());
        assertEquals(tripId, n.trip().id());
        assertEquals("강릉 여행", n.trip().title());
        assertEquals(NotificationTarget.ITEM, n.feedback().target());
        assertEquals(1, n.feedback().dayNumber());
        assertEquals("경포대", n.feedback().itemTitle());
        assertEquals("경포대 갈 때 편한 신발 필수예요!", n.feedback().preview());
    }

    @Test
    void dayAndTripLevelFeedbackRecordTargetAccordingly() {
        feedbackService.create(reviewer.getId(), tripId, new CreateFeedbackRequest("1일차 좋아요", dayId, null, List.of()));
        feedbackService.create(reviewer.getId(), tripId, new CreateFeedbackRequest("전체 좋아요", null, null, List.of()));

        List<NotificationResponse> list = notificationService.getNotifications(owner.getId(), PageRequest.of(0, 10)).content();
        assertEquals(2, list.size());
        NotificationResponse trip = list.stream().filter(n -> n.feedback().target() == NotificationTarget.TRIP).findFirst().orElseThrow();
        NotificationResponse day = list.stream().filter(n -> n.feedback().target() == NotificationTarget.DAY).findFirst().orElseThrow();
        assertNull(trip.feedback().dayNumber());
        assertEquals(1, day.feedback().dayNumber());
        assertNull(day.feedback().itemTitle());
    }

    @Test
    void reviewerGetsNoNotificationAndOwnerWithSettingOffGetsNone() {
        feedbackService.create(reviewer.getId(), tripId, new CreateFeedbackRequest("참견", null, null, List.of()));
        assertEquals(0, notificationService.getUnreadCount(reviewer.getId()).unreadCount());

        owner.updateNotificationSettings(true, false, true, false); // 참견 알림 끔
        entityManager.flush();
        feedbackService.create(reviewer.getId(), tripId, new CreateFeedbackRequest("두 번째 참견", null, null, List.of()));
        assertEquals(1, notificationService.getUnreadCount(owner.getId()).unreadCount());
    }

    @Test
    void scrapCreatesNotificationWithoutFeedbackBlockAndFollowsFeedbackSetting() {
        savedTripService.save(reviewer.getId(), tripId, FeedItemType.PLAN);

        NotificationPageResponse page = notificationService.getNotifications(owner.getId(), PageRequest.of(0, 10));
        assertEquals(1, page.unreadCount());
        NotificationResponse n = page.content().getFirst();
        assertEquals(NotificationType.SCRAP, n.type());
        assertEquals("리뷰어", n.actor().name());
        assertEquals("강릉 여행", n.trip().title());
        assertNull(n.feedback());
        assertEquals(0, notificationService.getUnreadCount(reviewer.getId()).unreadCount());

        // 참견 알림 설정을 끄면 스크랩 알림도 같이 꺼진다
        owner.updateNotificationSettings(true, false, true, false);
        entityManager.flush();
        User another = userRepository.save(new User("another-" + UUID.randomUUID() + "@noti.test", "다른사람", "KR"));
        savedTripService.save(another.getId(), tripId, FeedItemType.PLAN);
        assertEquals(1, notificationService.getUnreadCount(owner.getId()).unreadCount());
    }

    @Test
    void actorProfileImageReflectsCurrentProfile() {
        reviewer.changeProfileImage("https://cdn.test/profiles/r/1.jpg");
        entityManager.flush();
        feedbackService.create(reviewer.getId(), tripId, new CreateFeedbackRequest("사진 확인", null, null, List.of()));

        NotificationResponse n = notificationService.getNotifications(owner.getId(), PageRequest.of(0, 10)).content().getFirst();
        assertEquals("https://cdn.test/profiles/r/1.jpg", n.actor().profileImageUrl());

        reviewer.changeProfileImage("https://cdn.test/profiles/r/2.jpg");
        entityManager.flush();
        assertEquals("https://cdn.test/profiles/r/2.jpg",
                notificationService.getNotifications(owner.getId(), PageRequest.of(0, 10)).content().getFirst().actor().profileImageUrl());
    }

    @Test
    void longContentIsTruncatedToPreview() {
        String longContent = "가".repeat(120);
        feedbackService.create(reviewer.getId(), tripId, new CreateFeedbackRequest(longContent, null, null, List.of()));

        String preview = notificationService.getNotifications(owner.getId(), PageRequest.of(0, 10)).content().getFirst().feedback().preview();
        assertEquals(61, preview.length());
        assertTrue(preview.endsWith("…"));
    }

    @Test
    void markReadAndMarkAllReadUpdateUnreadCountAndRejectOthersNotifications() {
        feedbackService.create(reviewer.getId(), tripId, new CreateFeedbackRequest("하나", null, null, List.of()));
        feedbackService.create(reviewer.getId(), tripId, new CreateFeedbackRequest("둘", null, null, List.of()));
        List<NotificationResponse> list = notificationService.getNotifications(owner.getId(), PageRequest.of(0, 10)).content();

        notificationService.markRead(owner.getId(), list.get(0).id());
        assertEquals(1, notificationService.getUnreadCount(owner.getId()).unreadCount());

        CustomException ex = assertThrows(CustomException.class, () -> notificationService.markRead(reviewer.getId(), list.get(1).id()));
        assertEquals(ErrorCode.NOTIFICATION_NOT_FOUND, ex.getErrorCode());

        notificationService.markAllRead(owner.getId());
        assertEquals(0, notificationService.getUnreadCount(owner.getId()).unreadCount());
        assertTrue(notificationService.getNotifications(owner.getId(), PageRequest.of(0, 10)).content().stream().allMatch(NotificationResponse::read));
    }

    @Test
    void deleteAllRemovesOnlyTheUsersNotifications() {
        feedbackService.create(reviewer.getId(), tripId, new CreateFeedbackRequest("하나", null, null, List.of()));
        feedbackService.create(reviewer.getId(), tripId, new CreateFeedbackRequest("둘", null, null, List.of()));
        notificationService.markRead(owner.getId(),
                notificationService.getNotifications(owner.getId(), PageRequest.of(0, 10)).content().get(0).id());
        // 리뷰어가 받은 스크랩 알림 (다른 사람 알림은 남아야 한다)
        LocalDate date = LocalDate.of(2026, 8, 1);
        UUID reviewerTripId = tripService.createTrip(reviewer.getId(), new CreateTripRequest("리뷰어 여행", date, date)).id();
        notificationService.notifyScrap(new ScrapNotificationCommand(
                reviewer.getId(), owner.getId(), owner.getName(), reviewerTripId, "리뷰어 여행", null));

        notificationService.deleteAll(owner.getId());

        // 읽은 알림까지 모두 지워진다
        assertEquals(0, notificationService.getUnreadCount(owner.getId()).unreadCount());
        assertTrue(notificationService.getNotifications(owner.getId(), PageRequest.of(0, 10)).content().isEmpty());
        assertEquals(1, notificationService.getUnreadCount(reviewer.getId()).unreadCount());
    }

    @Test
    void deletingTripRemovesItsNotifications() {
        feedbackService.create(reviewer.getId(), tripId, new CreateFeedbackRequest("참견", null, null, List.of()));
        entityManager.flush();
        entityManager.clear();

        tripService.deleteTrip(owner.getId(), tripId);

        assertEquals(0, notificationService.getUnreadCount(owner.getId()).unreadCount());
    }

    @Test
    void pushHappensOnlyAfterCommitWithNotificationAndUnreadCount() {
        try {
            feedbackService.create(reviewer.getId(), tripId, new CreateFeedbackRequest("커밋 뒤 푸시", null, null, List.of()));
            verify(sseRegistry, never()).send(any(), anyString(), any());

            TestTransaction.flagForCommit();
            TestTransaction.end();

            ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
            verify(sseRegistry).send(eq(owner.getId()), eq(NotificationService.EVENT_NOTIFICATION), payload.capture());
            NotificationPushEvent event = (NotificationPushEvent) payload.getValue();
            assertEquals("커밋 뒤 푸시", event.notification().feedback().preview());
            assertEquals(1, event.unreadCount());
            assertNotNull(event.notification().createdAt());

            // 읽음 처리도 커밋 뒤에 unread-count 이벤트를 보낸다
            TestTransaction.start();
            notificationService.markAllRead(owner.getId());
            verify(sseRegistry, never()).send(eq(owner.getId()), eq(NotificationService.EVENT_UNREAD_COUNT), any());
            TestTransaction.flagForCommit();
            TestTransaction.end();
            verify(sseRegistry).send(eq(owner.getId()), eq(NotificationService.EVENT_UNREAD_COUNT), any());

            // 전체 삭제도 커밋 뒤에 미읽음 0 을 보낸다
            TestTransaction.start();
            feedbackService.create(reviewer.getId(), tripId, new CreateFeedbackRequest("삭제될 참견", null, null, List.of()));
            TestTransaction.flagForCommit();
            TestTransaction.end();
            clearInvocations(sseRegistry);
            TestTransaction.start();
            notificationService.deleteAll(owner.getId());
            verify(sseRegistry, never()).send(any(), anyString(), any());
            TestTransaction.flagForCommit();
            TestTransaction.end();
            ArgumentCaptor<Object> countPayload = ArgumentCaptor.forClass(Object.class);
            verify(sseRegistry).send(eq(owner.getId()), eq(NotificationService.EVENT_UNREAD_COUNT), countPayload.capture());
            assertEquals(0, ((UnreadCountResponse) countPayload.getValue()).unreadCount());
        } finally {
            cleanUpCommittedFixtures();
        }
    }

    @Test
    void rollbackSuppressesPushAndLeavesNoNotification() {
        feedbackService.create(reviewer.getId(), tripId, new CreateFeedbackRequest("롤백될 참견", null, null, List.of()));
        UUID ownerId = owner.getId();

        TestTransaction.end(); // 기본 롤백

        verify(sseRegistry, never()).send(any(), anyString(), any());
        TestTransaction.start();
        assertEquals(0, notificationService.getUnreadCount(ownerId).unreadCount());
    }

    // 커밋한 데이터 정리 (장바구니 → 계획(참견·알림 포함) → 사용자 순, FK 때문). 실패해도 다른 테스트를 오염시키지 않게 finally 에서 호출
    private void cleanUpCommittedFixtures() {
        if (TestTransaction.isActive()) {
            TestTransaction.flagForRollback();
            TestTransaction.end();
        }
        TestTransaction.start();
        cartItemRepository.deleteAllByUserId(owner.getId());
        tripService.deleteTrip(owner.getId(), tripId);
        userRepository.delete(owner);
        userRepository.delete(reviewer);
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    private UUID addItem(UUID targetDayId, String contentId, String title) {
        CartItem cart = cartItemRepository.save(new CartItem(owner, contentId, title, null, "32", "12", null, null, null));
        return tripService.addTripItem(owner.getId(), tripId, targetDayId, new AddTripItemRequest(cart.getId(), null, null)).id();
    }
}
