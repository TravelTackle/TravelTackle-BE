package Timeout.travel_tackle.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    //Trip에 관련 예외
    INVALID_TRIP_DATE_RANGE(HttpStatus.BAD_REQUEST, "TRIP_001", "여행 종료일은 시작일보다 빠를 수 없습니다."),
    INVALID_DAY_NUMBER(HttpStatus.BAD_REQUEST, "TRIP_002", "여행 일차는 1 이상이어야 합니다."),
    INVALID_TRIP_ITEM_TIME_RANGE(HttpStatus.BAD_REQUEST, "TRIP_003", "일정 종료 시간은 시작 시간보다 빠를 수 없습니다."),
    INVALID_ORDER_INDEX(HttpStatus.BAD_REQUEST, "TRIP_004", "일정 순서는 0 이상이어야 합니다."),
    TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_005", "여행 계획을 찾을 수 없습니다."),
    TRIP_ACCESS_DENIED(HttpStatus.FORBIDDEN, "TRIP_006", "해당 여행 계획에 접근 권한이 없습니다."),
    TRIP_DAY_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_007", "여행 일차를 찾을 수 없습니다."),
    TRIP_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_008", "여행 일정 항목을 찾을 수 없습니다."),
    INVALID_TRIP_ITEM_ORDER(HttpStatus.BAD_REQUEST, "TRIP_009", "일정 항목 순서가 올바르지 않습니다."),
    TRIP_NOT_PUBLISHED(HttpStatus.NOT_FOUND, "TRIP_010", "공개된 여행 계획이 아닙니다."),
    CANNOT_SAVE_OWN_TRIP(HttpStatus.BAD_REQUEST, "TRIP_011", "본인의 여행 계획은 저장할 수 없습니다."),
    TRIP_ALREADY_SAVED(HttpStatus.CONFLICT, "TRIP_012", "이미 저장한 여행 계획입니다."),
    SAVED_TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_013", "저장한 여행 계획을 찾을 수 없습니다."),
    TRIP_PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_014", "여행 사진을 찾을 수 없습니다."),
    TRIP_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_015", "여행 기록을 찾을 수 없습니다."),
    TRIP_RECORD_ALREADY_EXISTS(HttpStatus.CONFLICT, "TRIP_016", "이미 작성한 여행 기록이 있습니다. 계획당 기록은 하나만 작성할 수 있습니다."),
    FEEDBACK_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_017", "피드백을 찾을 수 없습니다."),
    FEEDBACK_ACCESS_DENIED(HttpStatus.FORBIDDEN, "TRIP_018", "해당 피드백에 접근 권한이 없습니다."),
    CANNOT_FEEDBACK_OWN_TRIP(HttpStatus.BAD_REQUEST, "TRIP_019", "본인의 여행 계획에는 피드백을 남길 수 없습니다."),
    FEEDBACK_TARGET_CONFLICT(HttpStatus.BAD_REQUEST, "TRIP_020", "일차(day)와 일정 항목(item)을 동시에 지정할 수 없습니다."),
    FEEDBACK_RECOMMENDATION_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_021", "추천 장소를 찾을 수 없습니다."),
    TRIP_PUBLISH_REQUIRES_ITEMS(HttpStatus.BAD_REQUEST, "TRIP_022", "모든 일차에 일정이 1개 이상 있어야 전체공개할 수 있습니다."),
    PUBLISHED_TRIP_DAY_MUST_KEEP_ITEM(HttpStatus.BAD_REQUEST, "TRIP_023", "공개된 계획은 각 일차에 일정이 1개 이상 남아 있어야 합니다. 먼저 비공개로 전환해 주세요."),
    PUBLISHED_TRIP_DATES_LOCKED(HttpStatus.BAD_REQUEST, "TRIP_024", "공개된 계획은 날짜를 변경할 수 없습니다. 먼저 비공개로 전환해 주세요."),
    FEEDBACK_ALREADY_LIKED(HttpStatus.CONFLICT, "TRIP_025", "이미 좋아요를 누른 참견입니다."),
    FEEDBACK_LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_026", "좋아요를 누르지 않은 참견입니다."),

    //Auth에 관련 예외
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH_001", "이미 가입된 이메일입니다."),
    EMAIL_VERIFICATION_NOT_FOUND(HttpStatus.BAD_REQUEST, "AUTH_002", "이메일 인증 요청을 찾을 수 없습니다."),
    INVALID_EMAIL_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "AUTH_003", "이메일 인증번호가 올바르지 않습니다."),
    EMAIL_VERIFICATION_EXPIRED(HttpStatus.BAD_REQUEST, "AUTH_004", "이메일 인증번호가 만료되었습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "AUTH_005", "이메일 인증이 완료되지 않았습니다."),
    EMAIL_VERIFICATION_ALREADY_USED(HttpStatus.BAD_REQUEST, "AUTH_006", "이미 사용된 이메일 인증입니다."),
    EMAIL_VERIFICATION_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_007", "이메일 인증 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    EMAIL_DELIVERY_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_008", "인증 이메일을 발송하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    SOCIAL_EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH_009", "소셜 계정에서 이메일 정보를 제공받지 못했습니다."),
    SOCIAL_ACCOUNT_LINK_REQUIRED(HttpStatus.CONFLICT, "AUTH_010", "동일한 이메일 계정이 존재합니다. 로그인 후 소셜 계정을 연결해 주세요."),
    UNSUPPORTED_AUTH_PROVIDER(HttpStatus.BAD_REQUEST, "AUTH_011", "지원하지 않는 소셜 로그인 제공자입니다."),
    SOCIAL_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_012", "소셜 로그인에 실패했습니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "AUTH_013", "로그인이 필요합니다."),
    INVALID_LOGIN_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_014", "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_015", "유효하지 않은 리프레시 토큰입니다."),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_016", "만료된 리프레시 토큰입니다."),
    SOCIAL_EMAIL_NOT_VERIFIED(HttpStatus.UNAUTHORIZED, "AUTH_017", "인증되지 않은 소셜 이메일입니다."),
    // 비밀번호 재설정은 EMAIL_VERIFICATION_*(NOT_FOUND/EXPIRED/ALREADY_USED)와 달리 상태를 세분화하지 않고
    // 하나로 합친다 — 실패 시 계정 탈취로 이어질 수 있어, 공격자에게 상태 정보를 주지 않기 위한 의도적 차이.
    INVALID_OR_EXPIRED_PASSWORD_RESET_CODE(HttpStatus.BAD_REQUEST, "AUTH_018", "인증번호가 올바르지 않거나 만료되었습니다."),
    PASSWORD_RESET_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_019", "인증 시도 횟수를 초과했습니다. 재설정을 다시 요청해 주세요."),
    PASSWORD_RESET_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_020", "비밀번호 재설정 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    PASSWORD_RESET_DELIVERY_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_021", "비밀번호 재설정 이메일을 발송하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "AUTH_022", "현재 비밀번호가 올바르지 않습니다."),
    NO_LOCAL_PASSWORD(HttpStatus.BAD_REQUEST, "AUTH_023", "소셜 로그인 계정은 비밀번호를 변경할 수 없습니다."),
    SAME_AS_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "AUTH_024", "새 비밀번호는 현재 비밀번호와 달라야 합니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_025", "사용자를 찾을 수 없습니다."),

    //Tour API에 관련 예외
    TOUR_API_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "TOUR_001", "관광 API 키가 설정되지 않았습니다."),
    TOUR_API_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "TOUR_002", "관광 정보를 불러오지 못했습니다."),
    TOUR_CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "TOUR_003", "관광 콘텐츠를 찾을 수 없습니다."),
    INVALID_TOUR_SEARCH_CONDITION(HttpStatus.BAD_REQUEST, "TOUR_004", "관광 검색 조건이 올바르지 않습니다."),
    CART_ITEM_ALREADY_EXISTS(HttpStatus.CONFLICT, "CART_001", "이미 장바구니에 담긴 관광 콘텐츠입니다."),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CART_002", "장바구니 항목을 찾을 수 없습니다."),

    //Preference에 관련 예외
    PREFERENCE_ALREADY_EXISTS(HttpStatus.CONFLICT, "PREF_001", "이미 선호도가 등록되어 있습니다."),
    PREFERENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "PREF_002", "선호도 정보를 찾을 수 없습니다."),

    //이미지 업로드(S3)에 관련 예외
    IMAGE_STORAGE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_001", "이미지 저장소가 설정되지 않았습니다."),
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "IMAGE_002", "지원하지 않는 이미지 형식입니다. (jpeg, png, webp)"),
    IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "IMAGE_003", "이미지는 파일당 10MB, 요청 전체 50MB 이하여야 합니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_004", "이미지 저장소에 업로드하지 못했습니다. 저장소 설정(버킷·리전·자격 증명)을 확인해 주세요."),

    //알림에 관련 예외
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTI_001", "알림을 찾을 수 없습니다."),

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_002", "요청 값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_003", "요청한 경로를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_004", "허용되지 않은 HTTP 메서드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_001", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
