package Timeout.travel_tackle.trip.service;

import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.cart.repository.CartItemRepository;
import Timeout.travel_tackle.entity.CartItem;
import Timeout.travel_tackle.entity.Trip;
import Timeout.travel_tackle.entity.TripDay;
import Timeout.travel_tackle.entity.TripItem;
import Timeout.travel_tackle.entity.TripPhoto;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.trip.dto.TripDetailResponse;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import Timeout.travel_tackle.image.service.ImageStorageService;
import Timeout.travel_tackle.notification.repository.NotificationRepository;
import Timeout.travel_tackle.trip.dto.*;
import Timeout.travel_tackle.trip.repository.SavedTripRepository;
import Timeout.travel_tackle.trip.repository.TripDayRepository;
import Timeout.travel_tackle.trip.repository.TripItemRepository;
import Timeout.travel_tackle.trip.repository.TripPhotoRepository;
import Timeout.travel_tackle.trip.repository.TripRecordRepository;
import Timeout.travel_tackle.trip.repository.TripFeedbackRecommendationRepository;
import Timeout.travel_tackle.trip.repository.TripFeedbackRepository;
import Timeout.travel_tackle.trip.repository.TripQueryRepository;
import Timeout.travel_tackle.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final TripDayRepository tripDayRepository;
    private final TripItemRepository tripItemRepository;
    private final TripQueryRepository tripQueryRepository;
    private final TripPhotoRepository tripPhotoRepository;
    private final ImageStorageService imageStorageService;
    private final NotificationRepository notificationRepository;
    private final TripRecordRepository tripRecordRepository;
    private final SavedTripRepository savedTripRepository;
    private final UserRepository userRepository;
    private final CartItemRepository cartItemRepository;
    private final TripFeedbackRepository tripFeedbackRepository;
    private final TripFeedbackRecommendationRepository tripFeedbackRecommendationRepository;

    @Transactional
    public TripSummaryResponse createTrip(UUID userId, CreateTripRequest request) {
        User user = findUser(userId);
        Trip trip = new Trip(user, request.title(), request.startDate(), request.endDate());
        tripRepository.saveAndFlush(trip);
        generateTripDays(trip, request.startDate(), request.endDate());
        return TripSummaryResponse.from(trip);
    }

    @Transactional(readOnly = true)
    public List<TripSummaryResponse> getMyTrips(UUID userId) {
        User user = findUser(userId);
        return tripRepository.findAllByUserOrderByCreatedAtDesc(user)
                .stream().map(TripSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TripDetailResponse getTripDetail(UUID userId, UUID tripId) {
        Trip trip = findTripOwnedBy(userId, tripId);
        TripDetailResponse detail = tripQueryRepository.findDetail(trip);
        long saveCount = savedTripRepository.countGroupByOriginalTripIds(List.of(tripId)).stream()
                .findFirst().map(row -> (Long) row[1]).orElse(0L);
        return detail.withSaveCount(saveCount).withRegion(RegionLabelResolver.fromTripDetail(detail));
    }

    @Transactional
    public TripSummaryResponse updateTrip(UUID userId, UUID tripId, UpdateTripRequest request) {
        Trip trip = findTripOwnedBy(userId, tripId);
        boolean datesChanged = !trip.getStartDate().equals(request.startDate())
                || !trip.getEndDate().equals(request.endDate());
        // 날짜가 바뀌면 일차·일정을 전부 다시 만들어 빈 일차가 생기므로, 공개 상태에서는 막는다
        if (datesChanged && trip.isPublished()) {
            throw new CustomException(ErrorCode.PUBLISHED_TRIP_DATES_LOCKED);
        }

        trip.updateSchedule(request.title(), request.startDate(), request.endDate());

        if (datesChanged) {
            // 피드백 참조만 null 처리 (텍스트 보존) → 일차·아이템만 삭제
            tripQueryRepository.bulkNullifyFeedbackReferences(trip);
            tripQueryRepository.bulkDeleteDaysAndItems(trip);
            generateTripDays(trip, request.startDate(), request.endDate());
        }

        return TripSummaryResponse.from(trip);
    }

    @Transactional
    public void deleteTrip(UUID userId, UUID tripId) {
        Trip trip = findTripOwnedBy(userId, tripId);
        // FK 제약 준수 삭제 순서:
        // 기록(사진→기록), 저장 이력, 피드백(추천→피드백), 일정(아이템→일차), 여행
        tripRecordRepository.findByTrip(trip).ifPresent(record -> {
            List<String> photoUrls = tripPhotoRepository.findAllByRecordOrderByUploadedAtAsc(record).stream()
                    .map(TripPhoto::getImageUrl).toList();
            tripPhotoRepository.deleteAllByRecord(record);
            tripPhotoRepository.flush();
            tripRecordRepository.delete(record);
            tripRecordRepository.flush();
            imageStorageService.deleteAfterCommit(userId, photoUrls);
        });
        savedTripRepository.deleteAllByOriginalTrip(trip);
        // 이 trip이 누군가의 "복사한 계획"으로 참조되고 있었다면(FK) 그 참조만 끊는다 — 스크랩 이력은 유지
        savedTripRepository.clearCopiedTripReference(trip);
        // bulkDeleteByTrip 내부에서 피드백 추천→피드백→아이템→일차 순으로 삭제
        deleteAllDaysAndItems(trip);
        notificationRepository.deleteAllByTripId(trip.getId()); // FK 는 없지만 사라진 계획의 알림은 의미가 없다
        tripRepository.delete(trip);
    }

    @Transactional
    public TripItemResponse addTripItem(UUID userId, UUID tripId, UUID dayId, AddTripItemRequest request) {
        Trip trip = findTripOwnedBy(userId, tripId);
        TripDay day = findDayInTrip(dayId, trip);
        CartItem cartItem = cartItemRepository.findByIdAndUserId(request.cartItemId(), userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CART_ITEM_NOT_FOUND));
        List<TripItem> existing = tripItemRepository.findAllByTripDayOrderByOrderIndex(day);
        int nextIndex = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getOrderIndex() + 1;
        TripItem item = new TripItem(day, cartItem.getTourApiContentId(), cartItem.getCachedTitle(),
                cartItem.getCachedImageUrl(), cartItem.getCachedRegionCode(), cartItem.getCachedContentTypeId(),
                cartItem.getCachedLclsSystm1(), cartItem.getCachedLclsSystm2(), cartItem.getCachedLclsSystm3(),
                request.startTime(), request.endTime(), nextIndex, null, null);
        tripItemRepository.save(item);
        trip.touch();
        return TripItemResponse.from(item);
    }

    @Transactional
    public TripItemResponse updateTripItem(UUID userId, UUID tripId, UUID dayId, UUID itemId,
                                           UpdateTripItemRequest request) {
        Trip trip = findTripOwnedBy(userId, tripId);
        TripDay day = findDayInTrip(dayId, trip);
        TripItem item = findItemInDay(itemId, day);
        item.changeTime(request.startTime(), request.endTime());
        if (request.memo() != null) {
            item.changeMemo(request.memo());
        }
        trip.touch();
        return TripItemResponse.from(item);
    }

    @Transactional
    public void deleteTripItem(UUID userId, UUID tripId, UUID dayId, UUID itemId) {
        Trip trip = findTripOwnedBy(userId, tripId);
        TripDay day = findDayInTrip(dayId, trip);
        TripItem item = findItemInDay(itemId, day);
        if (trip.isPublished() && tripItemRepository.countByTripDay(day) <= 1) {
            throw new CustomException(ErrorCode.PUBLISHED_TRIP_DAY_MUST_KEEP_ITEM);
        }
        tripFeedbackRepository.clearTripItemById(itemId);
        tripItemRepository.delete(item);
        trip.touch();
    }

    /**
     * 드래그앤드롭 같은 날 순서 변경.
     * unique(trip_day_id, order_index) 제약 충돌 방지를 위해 2단계 업데이트:
     *   1단계) 기존 index를 size 이상의 임시값으로 올려서 충돌 없이 flush
     *   2단계) 최종 index(0, 1, 2, ...)로 설정
     */
    @Transactional
    public List<TripItemResponse> reorderTripItems(UUID userId, UUID tripId, UUID dayId,
                                                   ReorderTripItemsRequest request) {
        Trip trip = findTripOwnedBy(userId, tripId);
        TripDay day = findDayInTrip(dayId, trip);
        List<TripItem> existing = tripItemRepository.findAllByTripDayOrderByOrderIndex(day);

        Set<UUID> dayItemIds = existing.stream().map(TripItem::getId).collect(Collectors.toSet());
        Set<UUID> requestedItemIds = new HashSet<>(request.itemIds());
        if (requestedItemIds.size() != request.itemIds().size()
                || !requestedItemIds.equals(dayItemIds)) {
            throw new CustomException(ErrorCode.INVALID_TRIP_ITEM_ORDER);
        }

        Map<UUID, TripItem> itemMap = existing.stream()
                .collect(Collectors.toMap(TripItem::getId, i -> i));

        int size = request.itemIds().size();

        moveToTemporaryOrders(existing);
        tripItemRepository.flush();

        // 2단계: 최종 순서 적용
        List<TripItem> ordered = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            TripItem item = itemMap.get(request.itemIds().get(i));
            item.changeOrder(i);
            ordered.add(item);
        }
        tripItemRepository.saveAll(ordered);
        trip.touch();

        return ordered.stream().map(TripItemResponse::from).toList();
    }

    /**
     * 드래그앤드롭으로 다른 날(TripDay)로 관광지 이동.
     * newOrderIndex가 null이면 대상 일차 맨 끝에 추가.
     */
    @Transactional
    public TripItemResponse moveTripItem(UUID userId, UUID tripId, UUID itemId,
                                         MoveTripItemRequest request) {
        Trip trip = findTripOwnedBy(userId, tripId);
        TripItem item = tripItemRepository.findById(itemId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRIP_ITEM_NOT_FOUND));
        if (!item.getTripDay().getTrip().getId().equals(tripId)) {
            throw new CustomException(ErrorCode.TRIP_ACCESS_DENIED);
        }

        TripDay newDay = findDayInTrip(request.newDayId(), trip);
        TripDay currentDay = item.getTripDay();
        List<TripItem> sourceItems = new ArrayList<>(
                tripItemRepository.findAllByTripDayOrderByOrderIndex(currentDay));

        if (currentDay.getId().equals(newDay.getId())) {
            moveWithinSameDay(item, sourceItems, request.newOrderIndex());
            trip.touch();
            return TripItemResponse.from(item);
        }
        if (trip.isPublished() && sourceItems.size() <= 1) {
            throw new CustomException(ErrorCode.PUBLISHED_TRIP_DAY_MUST_KEEP_ITEM);
        }

        List<TripItem> targetItems = new ArrayList<>(
                tripItemRepository.findAllByTripDayOrderByOrderIndex(newDay));
        sourceItems.remove(item);
        int newIndex = resolveInsertionIndex(request.newOrderIndex(), targetItems.size());

        List<TripItem> affectedItems = new ArrayList<>(sourceItems.size() + targetItems.size() + 1);
        affectedItems.addAll(sourceItems);
        affectedItems.add(item);
        affectedItems.addAll(targetItems);
        moveToTemporaryOrdersForEachDay(affectedItems);
        tripItemRepository.flush();

        targetItems.add(newIndex, item);
        applyOrder(sourceItems);
        applyOrder(targetItems);
        item.moveTo(newDay, newIndex);
        tripItemRepository.saveAll(affectedItems);
        trip.touch();

        return TripItemResponse.from(item);
    }

    // --- 계획 공개/비공개 ---

    @Transactional
    public TripSummaryResponse publishTrip(UUID userId, UUID tripId, String comment) {
        Trip trip = findTripOwnedBy(userId, tripId);
        List<Integer> emptyDays = findEmptyDayNumbers(trip);
        if (!emptyDays.isEmpty()) {
            String days = emptyDays.stream().map(n -> "Day " + n).collect(Collectors.joining(", "));
            throw new CustomException(ErrorCode.TRIP_PUBLISH_REQUIRES_ITEMS,
                    ErrorCode.TRIP_PUBLISH_REQUIRES_ITEMS.getMessage() + " 비어 있는 일차: " + days);
        }
        // request body 자체가 없으면(레거시 호출) comment는 null — 기존 코멘트를 건드리지 않는다.
        // 이미 공개 중인 계획을 다시 이 엔드포인트로 호출해 코멘트만 바꾸는 것도 허용한다(publish는 멱등).
        if (comment != null) {
            trip.updateComment(comment.isBlank() ? null : comment);
        }
        trip.publish();
        return TripSummaryResponse.from(trip);
    }

    private List<Integer> findEmptyDayNumbers(Trip trip) {
        Set<UUID> daysWithItems = tripItemRepository.countGroupByDay(trip).stream()
                .map(row -> (UUID) row[0])
                .collect(Collectors.toSet());
        return tripDayRepository.findAllByTripOrderByDayNumber(trip).stream()
                .filter(day -> !daysWithItems.contains(day.getId()))
                .map(TripDay::getDayNumber)
                .toList();
    }

    @Transactional
    public TripSummaryResponse unpublishTrip(UUID userId, UUID tripId) {
        Trip trip = findTripOwnedBy(userId, tripId);
        trip.unpublish();
        return TripSummaryResponse.from(trip);
    }

    // --- 내부 헬퍼 ---

    private void generateTripDays(Trip trip, LocalDate start, LocalDate end) {
        List<TripDay> days = new ArrayList<>();
        LocalDate cursor = start;
        int dayNumber = 1;
        while (!cursor.isAfter(end)) {
            days.add(new TripDay(trip, dayNumber++, cursor));
            cursor = cursor.plusDays(1);
        }
        tripDayRepository.saveAll(days);
    }

    private void deleteAllDaysAndItems(Trip trip) {
        tripQueryRepository.bulkDeleteByTrip(trip);
    }

    private void moveWithinSameDay(TripItem item, List<TripItem> items, Integer requestedIndex) {
        items.remove(item);
        int newIndex = resolveInsertionIndex(requestedIndex, items.size());

        List<TripItem> affectedItems = new ArrayList<>(items);
        affectedItems.add(item);
        moveToTemporaryOrders(affectedItems);
        tripItemRepository.flush();

        items.add(newIndex, item);
        applyOrder(items);
        tripItemRepository.saveAll(items);
    }

    private int resolveInsertionIndex(Integer requestedIndex, int maxIndex) {
        if (requestedIndex == null) {
            return maxIndex;
        }
        if (requestedIndex < 0 || requestedIndex > maxIndex) {
            throw new CustomException(ErrorCode.INVALID_TRIP_ITEM_ORDER);
        }
        return requestedIndex;
    }

    private void moveToTemporaryOrdersForEachDay(List<TripItem> items) {
        items.stream()
                .collect(Collectors.groupingBy(item -> item.getTripDay().getId()))
                .values()
                .forEach(this::moveToTemporaryOrders);
    }

    private void moveToTemporaryOrders(List<TripItem> items) {
        int temporaryStart = items.stream()
                .mapToInt(TripItem::getOrderIndex)
                .max()
                .orElse(-1) + 1;
        for (int i = 0; i < items.size(); i++) {
            items.get(i).changeOrder(temporaryStart + i);
        }
        tripItemRepository.saveAll(items);
    }

    private void applyOrder(List<TripItem> items) {
        for (int i = 0; i < items.size(); i++) {
            items.get(i).changeOrder(i);
        }
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHENTICATED));
    }

    private Trip findTripOwnedBy(UUID userId, UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRIP_NOT_FOUND));
        if (!trip.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.TRIP_ACCESS_DENIED);
        }
        return trip;
    }

    private TripDay findDayInTrip(UUID dayId, Trip trip) {
        TripDay day = tripDayRepository.findById(dayId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRIP_DAY_NOT_FOUND));
        if (!day.getTrip().getId().equals(trip.getId())) {
            throw new CustomException(ErrorCode.TRIP_DAY_NOT_FOUND);
        }
        return day;
    }

    private TripItem findItemInDay(UUID itemId, TripDay day) {
        TripItem item = tripItemRepository.findById(itemId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRIP_ITEM_NOT_FOUND));
        if (!item.getTripDay().getId().equals(day.getId())) {
            throw new CustomException(ErrorCode.TRIP_ITEM_NOT_FOUND);
        }
        return item;
    }
}
