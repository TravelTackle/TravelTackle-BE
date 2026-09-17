package Timeout.travel_tackle.trip.dto;

import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;

public enum FeedSort {
    LATEST,
    OLDEST,
    POPULAR,
    RELEVANCE;

    public static FeedSort from(String value) {
        for (FeedSort sort : values()) {
            if (sort.name().equalsIgnoreCase(value)) {
                return sort;
            }
        }
        throw new CustomException(ErrorCode.INVALID_INPUT);
    }
}
