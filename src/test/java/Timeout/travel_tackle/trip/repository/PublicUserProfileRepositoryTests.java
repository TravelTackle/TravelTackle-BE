package Timeout.travel_tackle.trip.repository;

import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.entity.Trip;
import Timeout.travel_tackle.entity.TripRecord;
import Timeout.travel_tackle.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * GET /api/feed/users/{userId}(/profile)이 비공개 계획/기록이나 타인의 데이터를
 * 절대 새어나가게 하지 않는지 리포지토리 레벨에서 검증한다.
 */
@DataJpaTest
class PublicUserProfileRepositoryTests {

    @Autowired UserRepository userRepository;
    @Autowired TripRepository tripRepository;
    @Autowired TripRecordRepository tripRecordRepository;

    @Test
    void findPublishedByUserExcludesPrivateTripsAndOtherUsersTrips() {
        User owner = userRepository.save(new User("owner@profile.test", "소유자", "KR"));
        User other = userRepository.save(new User("other@profile.test", "타인", "KR"));

        Trip ownerPublished = tripRepository.save(new Trip(owner, "소유자 공개", LocalDate.now(), LocalDate.now().plusDays(1)));
        ownerPublished.publish();
        tripRepository.save(new Trip(owner, "소유자 비공개", LocalDate.now(), LocalDate.now().plusDays(1))); // publish() 호출 안 함

        Trip otherPublished = tripRepository.save(new Trip(other, "타인 공개", LocalDate.now(), LocalDate.now().plusDays(1)));
        otherPublished.publish();
        tripRepository.flush();

        List<Trip> result = tripRepository.findPublishedByUser(owner, PageRequest.of(0, 10)).getContent();

        assertEquals(1, result.size());
        assertEquals(ownerPublished.getId(), result.getFirst().getId());
        assertEquals(1, tripRepository.countByUserAndPublishedTrue(owner));
    }

    @Test
    void popularityVariantAlsoExcludesPrivateAndOtherUsersTrips() {
        User owner = userRepository.save(new User("owner2@profile.test", "소유자2", "KR"));
        User other = userRepository.save(new User("other2@profile.test", "타인2", "KR"));

        Trip ownerPublished = tripRepository.save(new Trip(owner, "소유자 공개", LocalDate.now(), LocalDate.now().plusDays(1)));
        ownerPublished.publish();
        tripRepository.save(new Trip(owner, "소유자 비공개", LocalDate.now(), LocalDate.now().plusDays(1)));
        Trip otherPublished = tripRepository.save(new Trip(other, "타인 공개", LocalDate.now(), LocalDate.now().plusDays(1)));
        otherPublished.publish();
        tripRepository.flush();

        List<Trip> result = tripRepository.findPublishedByUserOrderByPopularity(owner, PageRequest.of(0, 10)).getContent();

        assertEquals(1, result.size());
        assertEquals(ownerPublished.getId(), result.getFirst().getId());
    }

    @Test
    void recordCountOnlyCountsRecordsUnderPublishedTrips() {
        User owner = userRepository.save(new User("owner3@profile.test", "소유자3", "KR"));

        Trip published = tripRepository.save(new Trip(owner, "공개", LocalDate.now(), LocalDate.now().plusDays(1)));
        published.publish();
        Trip privateTrip = tripRepository.save(new Trip(owner, "비공개", LocalDate.now(), LocalDate.now().plusDays(1)));

        tripRecordRepository.save(new TripRecord(published, "공개 기록", "내용"));
        tripRecordRepository.save(new TripRecord(privateTrip, "비공개 기록", "내용")); // 계획이 비공개면 기록도 세면 안 됨
        tripRecordRepository.flush();

        assertEquals(1, tripRecordRepository.countByTrip_UserAndTrip_PublishedTrue(owner));
    }
}
