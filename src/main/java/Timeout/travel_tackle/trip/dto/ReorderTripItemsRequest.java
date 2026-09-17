package Timeout.travel_tackle.trip.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record ReorderTripItemsRequest(
        @NotEmpty List<UUID> itemIds
) {}
