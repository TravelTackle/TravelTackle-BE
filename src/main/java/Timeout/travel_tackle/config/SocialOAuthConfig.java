package Timeout.travel_tackle.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConditionalOnProperty(name = "social.login.enabled", havingValue = "true")
public class SocialOAuthConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${KAKAO_CLIENT_ID:}") String kakaoClientId,
            @Value("${KAKAO_CLIENT_SECRET:}") String kakaoClientSecret,
            @Value("${GOOGLE_CLIENT_ID:}") String googleClientId,
            @Value("${GOOGLE_CLIENT_SECRET:}") String googleClientSecret
    ) {
        List<ClientRegistration> registrations = new ArrayList<>();
        addIfConfigured(registrations, kakao(kakaoClientId, kakaoClientSecret));
        addIfConfigured(registrations, google(googleClientId, googleClientSecret));
        if (registrations.isEmpty()) {
            throw new IllegalStateException("소셜 로그인이 활성화되어 있으나 설정된 제공자 자격 증명이 없습니다.");
        }
        return new InMemoryClientRegistrationRepository(registrations);
    }

    private ClientRegistration kakao(String clientId, String clientSecret) {
        if (!credentialsPresent(clientId, clientSecret)) {
            return null;
        }
        return ClientRegistration.withRegistrationId("kakao")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("profile_nickname")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .userInfoUri("https://kapi.kakao.com/v2/user/me")
                .userNameAttributeName("id")
                .clientName("Kakao")
                .build();
    }

    private ClientRegistration google(String clientId, String clientSecret) {
        if (!credentialsPresent(clientId, clientSecret)) {
            return null;
        }
        return CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .scope("openid", "profile", "email")
                .build();
    }

    private boolean credentialsPresent(String clientId, String clientSecret) {
        return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
    }

    private void addIfConfigured(List<ClientRegistration> registrations, ClientRegistration registration) {
        if (registration != null) {
            registrations.add(registration);
        }
    }
}
