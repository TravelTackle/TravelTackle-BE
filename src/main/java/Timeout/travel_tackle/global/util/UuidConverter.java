package Timeout.travel_tackle.global.util;

import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;

import java.util.UUID;

public class UuidConverter {

    private UuidConverter() {}

    public static UUID fromSubject(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.UNAUTHENTICATED);
        }
    }
}
