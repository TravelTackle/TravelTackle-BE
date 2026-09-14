package Timeout.travel_tackle.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleInvalidInput(
            Exception exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT;
        log.warn("Invalid request: path={}, type={}", request.getRequestURI(), exception.getClass().getSimpleName());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, resolveMessage(errorCode, errorCode.getMessage(), request), request.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.IMAGE_TOO_LARGE;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, request.getRequestURI()));
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(
            CustomException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        log.warn("Handled custom exception: code={}, path={}", errorCode.getCode(), request.getRequestURI());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, resolveMessage(errorCode, exception.getMessage(), request), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        log.error("Unhandled exception: path={}", request.getRequestURI(), exception);

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, resolveMessage(errorCode, errorCode.getMessage(), request), request.getRequestURI()));
    }

    /**
     * Accept-Language가 en으로 시작하고 해당 코드에 영어 메시지가 준비돼 있으면 그걸 쓰고,
     * 아니면 기본(한국어 또는 상황별 커스텀) 메시지를 그대로 쓴다. 프론트는 앱에서 선택한 언어를
     * 그대로 이 헤더에 실어 보내야 한다(브라우저 기본값에 맡기지 않음).
     */
    private String resolveMessage(ErrorCode errorCode, String defaultMessage, HttpServletRequest request) {
        String acceptLanguage = request.getHeader("Accept-Language");
        boolean english = acceptLanguage != null && acceptLanguage.toLowerCase().startsWith("en");
        return english && errorCode.getMessageEn() != null ? errorCode.getMessageEn() : defaultMessage;
    }
}
