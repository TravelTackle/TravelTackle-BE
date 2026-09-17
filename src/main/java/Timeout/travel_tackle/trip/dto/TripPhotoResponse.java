package Timeout.travel_tackle.trip.dto;

import Timeout.travel_tackle.entity.TripPhoto;

import java.time.LocalDateTime;
import java.util.UUID;

public record TripPhotoResponse(
        UUID id,
        String imageUrl,
        String caption,
        LocalDateTime uploadedAt
) {
    public static TripPhotoResponse from(TripPhoto photo) {
        return new TripPhotoResponse(
                photo.getId(),
                photo.getImageUrl(),
                photo.getCaption(),
                photo.getUploadedAt()
        );
    }
}
