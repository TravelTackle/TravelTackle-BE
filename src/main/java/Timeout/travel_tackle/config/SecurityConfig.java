package Timeout.travel_tackle.config;

import Timeout.travel_tackle.auth.social.SignedCookieOAuth2AuthorizationRequestRepository;
import Timeout.travel_tackle.auth.social.SocialOAuthFailureHandler;
import Timeout.travel_tackle.auth.social.SocialOAuthSuccessHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            BearerTokenResolver bearerTokenResolver,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            SignedCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository,
            SocialOAuthSuccessHandler socialOAuthSuccessHandler,
            SocialOAuthFailureHandler socialOAuthFailureHandler,
            ApiAuthenticationEntryPoint apiAuthenticationEntryPoint
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/email-verifications",
                                "/api/auth/email-verifications/confirm",
                                "/api/auth/signup",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/auth/password-resets",
                                "/api/auth/password-resets/confirm"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tour/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/feed/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/trips/*/feedback").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/trips/*/feedback/all").permitAll()
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/h2-console/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                // oauth2Login 이 등록하는 로그인 리다이렉트보다 먼저 매칭되도록 API 경로 진입점을 앞에 둔다
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(apiAuthenticationEntryPoint,
                                PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
                )
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver)
                        .authenticationEntryPoint(apiAuthenticationEntryPoint)
                        .jwt(Customizer.withDefaults())
                );

        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(oauth2 -> oauth2
                    .authorizationEndpoint(endpoint -> endpoint
                            .authorizationRequestRepository(authorizationRequestRepository))
                    .successHandler(socialOAuthSuccessHandler)
                    .failureHandler(socialOAuthFailureHandler)
            );
        }

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
