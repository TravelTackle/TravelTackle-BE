package Timeout.travel_tackle.trip.dto;

import Timeout.travel_tackle.entity.TripItem;

import java.time.LocalTime;
import java.util.UUID;

public record TripItemResponse(
        UUID id,
        String tourApiContentId,
        String cachedTitle,
        String cachedImageUrl,
        String contentTypeId,
        String address,
        String memo,
        LocalTime startTime,
        LocalTime endTime,
        int orderIndex
) {
    public static TripItemResponse from(TripItem item) {
        return new TripItemResponse(
                item.getId(),
                item.getTourApiContentId(),
                item.getCachedTitle(),
                item.getCachedImageUrl(),
                item.getContentTypeId(),
                item.getAddress(),
                item.getMemo(),
                item.getStartTime(),
                item.getEndTime(),
                item.getOrderIndex()
        );
    }
}
