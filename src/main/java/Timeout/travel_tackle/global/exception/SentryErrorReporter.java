package Timeout.travel_tackle.global.exception;

import io.sentry.IScope;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 예외를 Sentry 로 보낼 때 "어떤 API·어떤 화면·어떤 파라미터"를 태그와 컨텍스트로 붙인다.
 * DSN 이 없으면 Sentry.captureException 은 아무 일도 하지 않으므로 로컬에서는 서버 로그만 남는다.
 */
@Component
public class SentryErrorReporter {

    public static final String SCREEN_HEADER = "X-Client-Screen"; // 프론트가 현재 화면 이름을 실어 보내는 헤더
    private static final List<String> SENSITIVE_PARAM_KEYWORDS = List.of("password", "token", "secret", "code");

    public void report(Throwable exception, HttpServletRequest request, SentryLevel level, String errorCode) {
        Sentry.captureException(exception, scope -> configureScope(scope, exception, request, level, errorCode));
    }

    void configureScope(IScope scope, Throwable exception, HttpServletRequest request, SentryLevel level, String errorCode) {
        String route = routePattern(request);
        scope.setLevel(level);
        scope.setTag("api", request.getMethod() + " " + route);
        String screen = request.getHeader(SCREEN_HEADER);
        if (StringUtils.hasText(screen)) {
            scope.setTag("screen", screen);
        }
        if (errorCode != null) {
            scope.setTag("error_code", errorCode);
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("method", request.getMethod());
        context.put("path", request.getRequestURI());
        context.put("route", route);
        context.put("query_params", maskedParams(request));
        Object pathVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathVariables != null) {
            context.put("path_variables", pathVariables);
        }
        scope.setContexts("request_details", context);
        // 같은 예외라도 API 별로 다른 이슈로 묶이게 한다 (기본 그룹핑은 스택트레이스 기준)
        scope.setFingerprint(List.of(exception.getClass().getName(), route));
    }

    // UUID 가 섞인 실제 경로 대신 /api/trips/{tripId}/record 같은 패턴을 쓴다
    private static String routePattern(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : request.getRequestURI();
    }

    private static Map<String, String> maskedParams(HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            String lower = key.toLowerCase(Locale.ROOT);
            boolean sensitive = SENSITIVE_PARAM_KEYWORDS.stream().anyMatch(lower::contains);
            params.put(key, sensitive ? "***" : String.join(",", values));
        });
        return params;
    }
}
