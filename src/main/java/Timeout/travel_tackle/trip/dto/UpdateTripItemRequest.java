package Timeout.travel_tackle.trip.dto;

import java.time.LocalTime;

public record UpdateTripItemRequest(
        LocalTime startTime,
        LocalTime endTime,
        String memo
) {}
