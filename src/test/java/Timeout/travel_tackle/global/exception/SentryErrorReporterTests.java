package Timeout.travel_tackle.global.exception;

import io.sentry.Scope;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SentryErrorReporterTests {

    private final SentryErrorReporter reporter = new SentryErrorReporter();

    @Test
    void attachesApiScreenErrorCodeContextAndFingerprint() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/trips/11111111-1111-1111-1111-111111111111/record");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/trips/{tripId}/record");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("tripId", "11111111-1111-1111-1111-111111111111"));
        request.addHeader(SentryErrorReporter.SCREEN_HEADER, "record-detail");
        request.addParameter("sort", "popular");
        request.addParameter("resetCode", "123456");
        Scope scope = new Scope(new SentryOptions());
        NullPointerException exception = new NullPointerException("boom");

        reporter.configureScope(scope, exception, request, SentryLevel.ERROR, "COMMON_001");

        assertEquals(SentryLevel.ERROR, scope.getLevel());
        assertEquals("GET /api/trips/{tripId}/record", scope.getTags().get("api"));
        assertEquals("record-detail", scope.getTags().get("screen"));
        assertEquals("COMMON_001", scope.getTags().get("error_code"));
        assertEquals(List.of("java.lang.NullPointerException", "/api/trips/{tripId}/record"), scope.getFingerprint());
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) scope.getContexts().get("request_details");
        assertEquals("/api/trips/{tripId}/record", details.get("route"));
        @SuppressWarnings("unchecked")
        Map<String, String> params = (Map<String, String>) details.get("query_params");
        assertEquals("popular", params.get("sort"));
        assertEquals("***", params.get("resetCode")); // 민감 파라미터는 가린다
        assertEquals(Map.of("tripId", "11111111-1111-1111-1111-111111111111"), details.get("path_variables"));
    }

    @Test
    void fallsBackToRawPathWithoutRouteAndSkipsScreenTagWhenHeaderMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        Scope scope = new Scope(new SentryOptions());

        reporter.configureScope(scope, new IllegalStateException("x"), request, SentryLevel.WARNING, null);

        assertEquals("POST /api/auth/login", scope.getTags().get("api"));
        assertNull(scope.getTags().get("screen"));
        assertNull(scope.getTags().get("error_code"));
        assertEquals(SentryLevel.WARNING, scope.getLevel());
    }
}
