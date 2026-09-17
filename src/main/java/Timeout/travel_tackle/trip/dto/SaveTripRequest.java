package Timeout.travel_tackle.trip.dto;

import Timeout.travel_tackle.entity.Enum.FeedItemType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SaveTripRequest(
        @NotNull UUID tripId,
        @NotNull FeedItemType sourceType
) {}
