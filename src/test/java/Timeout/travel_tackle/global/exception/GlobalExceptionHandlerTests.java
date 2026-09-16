package Timeout.travel_tackle.global.exception;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTests {

    @Test
    void brokenPipeWhileWritingJsonIsNotReportedOrAnswered() {
        SentryErrorReporter reporter = org.mockito.Mockito.mock(SentryErrorReporter.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(reporter);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/feed");
        Exception disconnect = new org.springframework.http.converter.HttpMessageNotWritableException(
                "Could not write JSON: ServletOutputStream failed to write", new IOException("Broken pipe"));

        assertNull(handler.handleUnexpectedException(disconnect, request));
        org.mockito.Mockito.verify(reporter, org.mockito.Mockito.never()).report(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        // 일반 예외는 여전히 500 + Sentry 보고
        assertEquals(500, handler.handleUnexpectedException(new IllegalStateException("real bug"), request).getStatusCode().value());
        org.mockito.Mockito.verify(reporter).report(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("COMMON_001"));
    }

    @Test
    void sseEmitterTimeoutIsSwallowedWithoutReporting() {
        SentryErrorReporter reporter = org.mockito.Mockito.mock(SentryErrorReporter.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(reporter);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notifications/stream");

        assertDoesNotThrow(() -> handler.handleAsyncTimeout(
                new org.springframework.web.context.request.async.AsyncRequestTimeoutException(), request));
        org.mockito.Mockito.verifyNoInteractions(reporter);
    }

    @Test
    void clientDisconnectDuringSseIsSwallowedWithoutWritingAResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(new SentryErrorReporter());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notifications/stream");

        assertDoesNotThrow(() -> handler.handleClientDisconnected(
                new AsyncRequestNotUsableException("Servlet container error notification for disconnected client",
                        new IOException("Broken pipe")), request));
    }
}
