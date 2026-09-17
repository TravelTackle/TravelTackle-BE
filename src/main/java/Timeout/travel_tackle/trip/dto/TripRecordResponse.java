package Timeout.travel_tackle.trip.dto;

import Timeout.travel_tackle.entity.TripPhoto;
import Timeout.travel_tackle.entity.TripRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TripRecordResponse(
        UUID id,
        String title,
        String content,
        LocalDateTime createdAt,
        List<TripPhotoResponse> photos
) {
    public static TripRecordResponse of(TripRecord record, List<TripPhoto> photos) {
        return new TripRecordResponse(
                record.getId(),
                record.getTitle(),
                record.getContent(),
                record.getCreatedAt(),
                photos.stream().map(TripPhotoResponse::from).toList()
        );
    }
}
