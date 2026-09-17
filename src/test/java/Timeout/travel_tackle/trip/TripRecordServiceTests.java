package Timeout.travel_tackle.trip;

import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import Timeout.travel_tackle.trip.dto.CreateTripRequest;
import Timeout.travel_tackle.trip.dto.TripRecordRequest;
import Timeout.travel_tackle.trip.dto.TripRecordRequest.PhotoEntry;
import Timeout.travel_tackle.trip.dto.TripRecordResponse;
import Timeout.travel_tackle.trip.service.TripRecordService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class TripRecordServiceTests {

    @Autowired TripRecordService tripRecordService;
    @Autowired TripService tripService;
    @Autowired UserRepository userRepository;

    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("rec-owner@example.com", "주인", "KR"));
        other = userRepository.save(new User("rec-other@example.com", "남", "KR"));
    }

    @Test
    void createsRecordWithContentAndPhotos() {
        UUID tripId = createTrip();

        TripRecordResponse record = tripRecordService.createRecord(owner.getId(), tripId,
                new TripRecordRequest("여행 제목", "정말 좋았던 여행",
                        List.of(new PhotoEntry("https://cdn.test/a.jpg", "첫날"),
                                new PhotoEntry("https://cdn.test/b.jpg", null))));

        assertEquals("정말 좋았던 여행", record.content());
        assertEquals(2, record.photos().size());
        assertNotNull(record.photos().getFirst().uploadedAt()); // flush로 채워짐

        TripRecordResponse fetched = tripRecordService.getRecord(owner.getId(), tripId);
        assertEquals(record.id(), fetched.id());
        assertEquals(2, fetched.photos().size());
    }

    @Test
    void allowsOnlyOneRecordPerTrip() {
        UUID tripId = createTrip();
        tripRecordService.createRecord(owner.getId(), tripId, onePhotoRecord("첫 기록"));

        CustomException exception = assertThrows(CustomException.class, () ->
                tripRecordService.createRecord(owner.getId(), tripId, onePhotoRecord("두 번째")));

        assertEquals(ErrorCode.TRIP_RECORD_ALREADY_EXISTS, exception.getErrorCode());
    }

    @Test
    void rejectsRecordOnSomeoneElsesTrip() {
        UUID tripId = createTrip();

        CustomException exception = assertThrows(CustomException.class, () ->
                tripRecordService.createRecord(other.getId(), tripId, onePhotoRecord("내꺼아님")));

        assertEquals(ErrorCode.TRIP_ACCESS_DENIED, exception.getErrorCode());
    }

    @Test
    void getRecordFailsWhenNoneWritten() {
        UUID tripId = createTrip();

        CustomException exception = assertThrows(CustomException.class, () ->
                tripRecordService.getRecord(owner.getId(), tripId));

        assertEquals(ErrorCode.TRIP_RECORD_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void updateReplacesContentAndPhotos() {
        UUID tripId = createTrip();
        tripRecordService.createRecord(owner.getId(), tripId,
                new TripRecordRequest("여행 제목", "처음",
                        List.of(new PhotoEntry("https://cdn.test/a.jpg", null),
                                new PhotoEntry("https://cdn.test/b.jpg", null))));

        TripRecordResponse updated = tripRecordService.updateRecord(owner.getId(), tripId,
                new TripRecordRequest("여행 제목", "수정됨",
                        List.of(new PhotoEntry("https://cdn.test/c.jpg", "새 사진"))));

        assertEquals("수정됨", updated.content());
        assertEquals(1, updated.photos().size());
        assertEquals("https://cdn.test/c.jpg", updated.photos().getFirst().imageUrl());
    }

    @Test
    void deleteRemovesRecord() {
        UUID tripId = createTrip();
        tripRecordService.createRecord(owner.getId(), tripId, onePhotoRecord("지울 기록"));

        tripRecordService.deleteRecord(owner.getId(), tripId);

        CustomException exception = assertThrows(CustomException.class, () ->
                tripRecordService.getRecord(owner.getId(), tripId));
        assertEquals(ErrorCode.TRIP_RECORD_NOT_FOUND, exception.getErrorCode());
    }

    private TripRecordRequest onePhotoRecord(String content) {
        return new TripRecordRequest("여행 제목", content, List.of(new PhotoEntry("https://cdn.test/x.jpg", null)));
    }

    private UUID createTrip() {
        LocalDate date = LocalDate.of(2026, 7, 1);
        return tripService.createTrip(owner.getId(),
                new CreateTripRequest("기록용 여행", date, date)).id();
    }
}
