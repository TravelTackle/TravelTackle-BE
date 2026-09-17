package Timeout.travel_tackle.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import io.sentry.SentryLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final SentryErrorReporter sentryErrorReporter;

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ErrorResponse> handleInvalidInput(
            Exception exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT;
        log.warn("Invalid request: path={}, type={}", request.getRequestURI(), exception.getClass().getSimpleName());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, request.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.IMAGE_TOO_LARGE;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, request.getRequestURI()));
    }

    // SSE 등 비동기 응답 중 클라이언트가 끊기면 컨테이너가 이 예외를 통보한다. 응답을 쓸 수 없는 상태이므로
    // 포괄 핸들러가 ErrorResponse 를 쓰려다 두 번째 예외를 내지 않도록 여기서 조용히 끝낸다.
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientDisconnected(AsyncRequestNotUsableException exception, HttpServletRequest request) {
        log.debug("Client disconnected during async response: path={}", request.getRequestURI());
    }

    // SSE 연결이 SseEmitter 타임아웃(30분)에 도달하면 스프링이 이 예외로 통보한다. 브라우저 EventSource 가 자동 재연결하므로 정상 동작이다
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout(AsyncRequestTimeoutException exception, HttpServletRequest request) {
        log.debug("Async request timed out (SSE emitter expiry): path={}", request.getRequestURI());
    }

    // HttpMessageNotWritableException("Could not write JSON: ... Broken pipe") 처럼 원인 사슬 어딘가에 소켓 IOException 이 있으면 클라이언트 끊김으로 본다
    static boolean isClientDisconnect(Throwable exception) {
        for (Throwable t = exception; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof java.io.IOException) {
                String message = t.getMessage() == null ? "" : t.getMessage();
                return message.contains("Broken pipe") || message.contains("Connection reset")
                        || t.getClass().getSimpleName().equals("ClientAbortException");
            }
        }
        return false;
    }

    // 매핑 없는 경로(봇 스캔·오타·favicon)는 서버 오류가 아니라 404. Sentry 에 보내지 않는다
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception exception, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;
        log.debug("No handler for path={}", request.getRequestURI());
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode, request.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception,
                                                                HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
        log.debug("Method not allowed: {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode, request.getRequestURI()));
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(
            CustomException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        log.warn("Handled custom exception: code={}, path={}", errorCode.getCode(), request.getRequestURI());
        // 4xx 는 사용자 실수라 보내지 않고, 외부 의존(메일·TourAPI·S3) 장애인 5xx 만 WARNING 으로 보낸다
        if (errorCode.getStatus().is5xxServerError()) {
            sentryErrorReporter.report(exception, request, SentryLevel.WARNING, errorCode.getCode());
        }

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        // 응답을 쓰는 도중 브라우저가 연결을 끊은 경우(페이지 이동, 요청 취소). 서버 장애가 아니고 응답도 못 쓰므로 조용히 끝낸다
        if (isClientDisconnect(exception)) {
            log.debug("Client disconnected while writing response: path={}", request.getRequestURI());
            return null;
        }
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        // 태그를 붙인 캡처를 먼저 보내고 나서 로그를 남긴다. sentry-logback 이 같은 예외를 다시 보내려 해도 SDK 중복 제거에 걸려 첫 이벤트만 남는다
        sentryErrorReporter.report(exception, request, SentryLevel.ERROR, errorCode.getCode());
        log.error("Unhandled exception: path={}", request.getRequestURI(), exception); // 서버 로그는 그대로 남긴다

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, request.getRequestURI()));
    }
}
