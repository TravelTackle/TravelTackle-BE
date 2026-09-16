package Timeout.travel_tackle.config;

import io.sentry.protocol.User;
import io.sentry.spring7.SentryUserProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

// DSN 이 비어 있으면 Sentry 자동 설정이 꺼지므로 이 빈도 같이 만들지 않는다
@Configuration
@ConditionalOnExpression("!'${sentry.dsn:}'.isBlank()")
public class SentryConfig {

    /** 어떤 사용자에게 난 오류인지 추적할 수 있게 JWT subject(사용자 UUID)만 붙인다. 이메일·이름은 보내지 않는다. */
    @Bean
    public SentryUserProvider sentryUserProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
                return null;
            }
            User user = new User();
            user.setId(jwt.getSubject());
            return user;
        };
    }
}
