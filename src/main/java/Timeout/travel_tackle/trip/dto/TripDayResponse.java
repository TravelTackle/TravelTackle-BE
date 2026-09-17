package Timeout.travel_tackle.trip.dto;

import Timeout.travel_tackle.entity.TripDay;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TripDayResponse(
        UUID id,
        int dayNumber,
        LocalDate date,
        List<TripItemResponse> items
) {
    public static TripDayResponse of(TripDay day, List<TripItemResponse> items) {
        return new TripDayResponse(day.getId(), day.getDayNumber(), day.getDate(), items);
    }
}
