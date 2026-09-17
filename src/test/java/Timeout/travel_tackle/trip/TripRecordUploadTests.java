package Timeout.travel_tackle.trip;

import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import Timeout.travel_tackle.image.service.ImageStorageService;
import Timeout.travel_tackle.image.storage.ImageStorageProperties;
import Timeout.travel_tackle.trip.dto.CreateTripRequest;
import Timeout.travel_tackle.trip.dto.TripRecordRequest;
import Timeout.travel_tackle.trip.dto.TripRecordRequest.PhotoEntry;
import Timeout.travel_tackle.trip.dto.TripRecordResponse;
import Timeout.travel_tackle.trip.dto.TripRecordUploadRequest;
import Timeout.travel_tackle.trip.service.TripRecordService;
import Timeout.travel_tackle.trip.service.TripService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S3Client 와 저장소 설정만 목으로 바꾸고 ImageStorageService 는 실제 구현을 태운다.
 * 커밋 이후 훅(afterCommit/afterCompletion)을 검증하는 테스트는 실제로 커밋한 뒤 데이터를 정리한다.
 */
@SpringBootTest
@Transactional
class TripRecordUploadTests {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    static final String BASE = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/";

    @Autowired TripRecordService tripRecordService;
    @Autowired TripService tripService;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager entityManager;

    @MockitoBean S3Client s3Client;
    @MockitoBean ImageStorageProperties properties;

    private User owner;
    private UUID tripId;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("owner-" + UUID.randomUUID() + "@upload.test", "주인", "KR"));
        LocalDate date = LocalDate.of(2026, 7, 1);
        tripId = tripService.createTrip(owner.getId(), new CreateTripRequest("여행", date, date)).id();

        when(properties.bucket()).thenReturn("test-bucket");
        when(properties.publicUrlOf(any())).thenAnswer(inv -> BASE + inv.getArgument(0));
        when(properties.keyOf(any())).thenAnswer(inv -> {
            String url = inv.getArgument(0);
            return url != null && url.startsWith(BASE) ? url.substring(BASE.length()) : null;
        });
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
    }

    @Test
    void createsRecordWithUploadedPhotosInOrderAndCaptions() {
        TripRecordResponse record = tripRecordService.createRecordWithUploads(owner.getId(), tripId,
                new TripRecordUploadRequest("제목", "내용", List.of(photo("a"), photo("b")), List.of("첫 장")));

        assertEquals(2, record.photos().size());
        assertTrue(record.photos().get(0).imageUrl().startsWith(BASE + "images/" + owner.getId() + "/"));
        assertEquals("첫 장", record.photos().get(0).caption());
        assertNull(record.photos().get(1).caption());
        verify(s3Client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void validatesEveryFileBeforeUploadingAny() {
        MockMultipartFile bogus = new MockMultipartFile("photos", "x.jpg", "image/jpeg", "not-an-image".getBytes());

        CustomException ex = assertThrows(CustomException.class, () ->
                tripRecordService.createRecordWithUploads(owner.getId(), tripId,
                        new TripRecordUploadRequest("제목", "내용", List.of(photo("a"), bogus), null)));

        assertEquals(ErrorCode.UNSUPPORTED_IMAGE_TYPE, ex.getErrorCode());
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void rejectsMoreCaptionsThanPhotos() {
        CustomException ex = assertThrows(CustomException.class, () ->
                tripRecordService.createRecordWithUploads(owner.getId(), tripId,
                        new TripRecordUploadRequest("제목", "내용", List.of(photo("a")), List.of("1", "2"))));
        assertEquals(ErrorCode.INVALID_INPUT, ex.getErrorCode());
    }

    @Test
    void doesNotUploadWhenRecordAlreadyExists() {
        tripRecordService.createRecordWithUploads(owner.getId(), tripId,
                new TripRecordUploadRequest("제목", "내용", List.of(photo("a")), null));

        CustomException ex = assertThrows(CustomException.class, () ->
                tripRecordService.createRecordWithUploads(owner.getId(), tripId,
                        new TripRecordUploadRequest("둘", "내용", List.of(photo("b")), null)));
        assertEquals(ErrorCode.TRIP_RECORD_ALREADY_EXISTS, ex.getErrorCode());
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void cleansUpAlreadyUploadedObjectsWhenALaterUploadFails() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build())
                .thenThrow(S3Exception.builder().statusCode(301).message("wrong endpoint").build());

        CustomException ex = assertThrows(CustomException.class, () ->
                tripRecordService.createRecordWithUploads(owner.getId(), tripId,
                        new TripRecordUploadRequest("제목", "내용", List.of(photo("a"), photo("b")), null)));

        assertEquals(ErrorCode.IMAGE_UPLOAD_FAILED, ex.getErrorCode());
        verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void updateDeletesOnlyRemovedObjectsAfterCommit() {
        TripRecordResponse created = tripRecordService.createRecordWithUploads(owner.getId(), tripId,
                new TripRecordUploadRequest("제목", "내용", List.of(photo("a"), photo("b")), null));
        String keptUrl = created.photos().get(0).imageUrl();
        String removedUrl = created.photos().get(1).imageUrl();

        // JSON 수정으로 첫 사진은 유지하고 둘째 사진만 뺀다
        tripRecordService.updateRecord(owner.getId(), tripId,
                new TripRecordRequest("제목2", "내용2", List.of(new PhotoEntry(keptUrl, "유지"))));

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        commit();
        verify(s3Client).deleteObject(argThat((DeleteObjectRequest r) -> removedUrl.endsWith(r.key())));
        verify(s3Client, never()).deleteObject(argThat((DeleteObjectRequest r) -> keptUrl.endsWith(r.key())));
        cleanUp();
    }

    @Test
    void deleteRecordRemovesUploadedObjectsAfterCommit() {
        tripRecordService.createRecordWithUploads(owner.getId(), tripId,
                new TripRecordUploadRequest("제목", "내용", List.of(photo("a"), photo("b")), null));

        tripRecordService.deleteRecord(owner.getId(), tripId);

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        commit();
        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
        cleanUp();
    }

    @Test
    void deleteTripRemovesRecordPhotosFromStorageAfterCommit() {
        tripRecordService.createRecordWithUploads(owner.getId(), tripId,
                new TripRecordUploadRequest("제목", "내용", List.of(photo("a")), null));

        // 운영에선 요청마다 영속성 컨텍스트가 새로 열리므로 같은 트랜잭션에서 로드된 엔티티를 분리한다
        entityManager.flush();
        entityManager.clear();
        tripService.deleteTrip(owner.getId(), tripId);

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        commit();
        verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
        TestTransaction.start();
        userRepository.delete(owner);
        commit();
    }

    private static void commit() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    // 커밋한 데이터가 다른 테스트에 남지 않게 지운다. deleteTrip 도 S3 삭제를 예약하므로 검증은 이 전에 끝내야 한다
    private void cleanUp() {
        TestTransaction.start();
        tripService.deleteTrip(owner.getId(), tripId);
        userRepository.delete(owner);
        commit();
    }

    private static MockMultipartFile photo(String name) {
        return new MockMultipartFile("photos", name + ".jpg", "image/jpeg", JPEG);
    }
}
