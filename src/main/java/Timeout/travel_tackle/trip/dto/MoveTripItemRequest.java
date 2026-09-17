package Timeout.travel_tackle.trip.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MoveTripItemRequest(
        @NotNull UUID newDayId,
        Integer newOrderIndex  // null이면 대상 일차 맨 끝에 추가
) {}
