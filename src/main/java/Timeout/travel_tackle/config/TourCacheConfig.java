package Timeout.travel_tackle.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class TourCacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                "tourAreas",
                "tourCategories",
                "tourContents",
                "tourNearby",
                "tourDetails",
                "tourFestivals",
                "tourStays",
                "tourRecommended",
                "tourContentsByLang",
                "tourFestivalsByLang",
                "tourFilteredByLang",
                "tourPetFlags"
        );
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(2_000)
                .expireAfterWrite(Duration.ofMinutes(30)));
        // 연관 관광지는 월 단위로만 바뀌고 계산 비용(외부 호출 최대 17회)이 커서 오래 캐싱
        manager.registerCustomCache("tourRelated", Caffeine.newBuilder()
                .maximumSize(2_000)
                .expireAfterWrite(Duration.ofHours(12))
                .build());
        return manager;
    }
}
