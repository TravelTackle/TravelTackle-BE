package Timeout.travel_tackle.trip.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.UUID;

public record AddTripItemRequest(
        @NotNull UUID cartItemId,
        LocalTime startTime,
        LocalTime endTime
) {}
