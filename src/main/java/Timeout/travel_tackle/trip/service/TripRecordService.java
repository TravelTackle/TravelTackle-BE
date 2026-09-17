package Timeout.travel_tackle.trip.service;

import Timeout.travel_tackle.entity.Trip;
import Timeout.travel_tackle.entity.TripPhoto;
import Timeout.travel_tackle.entity.TripRecord;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import Timeout.travel_tackle.image.service.ImageStorageService;
import Timeout.travel_tackle.trip.dto.TripRecordRequest;
import Timeout.travel_tackle.trip.dto.TripRecordRequest.PhotoEntry;
import Timeout.travel_tackle.trip.dto.TripRecordUploadRequest;
import Timeout.travel_tackle.trip.dto.TripRecordResponse;
import Timeout.travel_tackle.trip.repository.TripPhotoRepository;
import Timeout.travel_tackle.trip.repository.TripRecordRepository;
import Timeout.travel_tackle.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 여행 기록(후기). 계획당 1개, 내용 필수, 사진 1장 이상.
 */
@Service
@RequiredArgsConstructor
public class TripRecordService {

    private final TripRepository tripRepository;
    private final TripRecordRepository tripRecordRepository;
    private final TripPhotoRepository tripPhotoRepository;
    private final ImageStorageService imageStorageService;

    @Transactional
    public TripRecordResponse createRecord(UUID userId, UUID tripId, TripRecordRequest request) {
        Trip trip = findTripOwnedBy(userId, tripId);
        if (tripRecordRepository.existsByTrip(trip)) {
            throw new CustomException(ErrorCode.TRIP_RECORD_ALREADY_EXISTS);
        }
        TripRecord record = tripRecordRepository.save(new TripRecord(trip, request.title(), request.content()));
        savePhotos(record, request.photos());
        return buildResponse(record);
    }

    /** 사진 파일을 S3 에 올린 뒤 기록을 만든다. 권한·중복 검사를 업로드보다 먼저 해서 고아 객체를 줄인다. */
    @Transactional
    public TripRecordResponse createRecordWithUploads(UUID userId, UUID tripId, TripRecordUploadRequest request) {
        Trip trip = findTripOwnedBy(userId, tripId);
        if (tripRecordRepository.existsByTrip(trip)) {
            throw new CustomException(ErrorCode.TRIP_RECORD_ALREADY_EXISTS);
        }
        List<PhotoEntry> photos = uploadPhotos(userId, request);
        TripRecord record = tripRecordRepository.save(new TripRecord(trip, request.title(), request.content()));
        savePhotos(record, photos);
        return buildResponse(record);
    }

    @Transactional(readOnly = true)
    public TripRecordResponse getRecord(UUID userId, UUID tripId) {
        Trip trip = findTripOwnedBy(userId, tripId);
        TripRecord record = findRecordOf(trip);
        return buildResponse(record);
    }

    @Transactional
    public TripRecordResponse updateRecord(UUID userId, UUID tripId, TripRecordRequest request) {
        Trip trip = findTripOwnedBy(userId, tripId);
        TripRecord record = findRecordOf(trip);
        record.updateTitle(request.title());
        record.updateContent(request.content());
        replacePhotos(userId, record, request.photos());
        return buildResponse(record);
    }

    @Transactional
    public TripRecordResponse updateRecordWithUploads(UUID userId, UUID tripId, TripRecordUploadRequest request) {
        Trip trip = findTripOwnedBy(userId, tripId);
        TripRecord record = findRecordOf(trip);
        List<PhotoEntry> photos = uploadPhotos(userId, request);
        record.updateTitle(request.title());
        record.updateContent(request.content());
        replacePhotos(userId, record, photos);
        return buildResponse(record);
    }

    @Transactional
    public void deleteRecord(UUID userId, UUID tripId) {
        Trip trip = findTripOwnedBy(userId, tripId);
        TripRecord record = findRecordOf(trip);
        List<TripPhoto> oldPhotos = tripPhotoRepository.findAllByRecordOrderByUploadedAtAsc(record);
        // 사진(자식) → 기록(부모) 순으로 제거해야 FK 제약 위반이 없다
        tripPhotoRepository.deleteAllByRecord(record);
        tripPhotoRepository.flush();
        tripRecordRepository.delete(record);
        imageStorageService.deleteAfterCommit(userId, urlsOf(oldPhotos));
    }

    /**
     * 모든 파일을 먼저 검증한 뒤 올린다. 중간에 실패하면 이미 올린 객체를 지우고,
     * 이후 DB 가 롤백돼도 올린 객체가 남지 않도록 롤백 훅을 건다.
     */
    private List<PhotoEntry> uploadPhotos(UUID userId, TripRecordUploadRequest request) {
        if (request.captions() != null && request.captions().size() > request.photos().size()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        request.photos().forEach(imageStorageService::validate);

        List<PhotoEntry> uploaded = new ArrayList<>();
        try {
            for (int i = 0; i < request.photos().size(); i++) {
                String url = imageStorageService.upload(userId, request.photos().get(i));
                uploaded.add(new PhotoEntry(url, request.captionAt(i)));
            }
        } catch (RuntimeException e) {
            uploaded.forEach(entry -> imageStorageService.deleteQuietly(userId, entry.imageUrl()));
            throw e;
        }
        imageStorageService.deleteOnRollback(userId, uploaded.stream().map(PhotoEntry::imageUrl).toList());
        return uploaded;
    }

    // 사진은 전체 교체. 새 목록에 남지 않는 이전 객체만 커밋 뒤에 지운다
    private void replacePhotos(UUID userId, TripRecord record, List<PhotoEntry> photos) {
        List<TripPhoto> oldPhotos = tripPhotoRepository.findAllByRecordOrderByUploadedAtAsc(record);
        tripPhotoRepository.deleteAllByRecord(record);
        tripPhotoRepository.flush();
        savePhotos(record, photos);

        Set<String> kept = photos.stream().map(PhotoEntry::imageUrl).collect(Collectors.toSet());
        List<String> removed = urlsOf(oldPhotos).stream().filter(url -> !kept.contains(url)).toList();
        imageStorageService.deleteAfterCommit(userId, removed);
    }

    private static List<String> urlsOf(List<TripPhoto> photos) {
        return photos.stream().map(TripPhoto::getImageUrl).toList();
    }

    private void savePhotos(TripRecord record, List<PhotoEntry> entries) {
        List<TripPhoto> photos = entries.stream()
                .map(entry -> new TripPhoto(record, entry.imageUrl(), entry.caption()))
                .toList();
        // flush 해야 @CreationTimestamp(uploadedAt)가 채워진 상태로 응답할 수 있다
        tripPhotoRepository.saveAllAndFlush(photos);
    }

    private TripRecordResponse buildResponse(TripRecord record) {
        return TripRecordResponse.of(record,
                tripPhotoRepository.findAllByRecordOrderByUploadedAtAsc(record));
    }

    private TripRecord findRecordOf(Trip trip) {
        return tripRecordRepository.findByTrip(trip)
                .orElseThrow(() -> new CustomException(ErrorCode.TRIP_RECORD_NOT_FOUND));
    }

    private Trip findTripOwnedBy(UUID userId, UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRIP_NOT_FOUND));
        if (!trip.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.TRIP_ACCESS_DENIED);
        }
        return trip;
    }
}
