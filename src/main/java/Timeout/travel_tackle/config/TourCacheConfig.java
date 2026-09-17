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
                "tourFilteredByLang"
        );
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(2_000)
                .expireAfterWrite(Duration.ofMinutes(30)));
        return manager;
    }
}
