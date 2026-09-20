package Timeout.travel_tackle.trip.pet;

import Timeout.travel_tackle.trip.service.PetFriendlyBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * APP_PET_BACKFILL_ON_STARTUP=true 일 때 부팅 직후 백그라운드로 한 번 백필한다.
 * 관광공사 호출이 장소 수만큼 나가므로 기본은 꺼 두고, 기능 도입 직후 한 번만 켜서 돌린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.pet.backfill-on-startup", havingValue = "true")
public class PetFriendlyBackfillRunner implements ApplicationRunner {

    private final PetFriendlyBackfillService backfillService;

    @Override
    public void run(ApplicationArguments args) {
        Thread thread = new Thread(() -> {
            try {
                backfillService.backfill();
            } catch (RuntimeException e) {
                log.warn("반려동물 동반 여부 백필 중단", e);
            }
        }, "pet-friendly-backfill");
        thread.setDaemon(true);
        thread.start();
    }
}
