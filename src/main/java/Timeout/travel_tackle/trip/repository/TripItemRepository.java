package Timeout.travel_tackle.trip.repository;

import Timeout.travel_tackle.entity.Trip;
import Timeout.travel_tackle.entity.TripDay;
import Timeout.travel_tackle.entity.TripItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TripItemRepository extends JpaRepository<TripItem, UUID> {
    List<TripItem> findAllByTripDayOrderByOrderIndex(TripDay tripDay);

    long countByTripDay(TripDay tripDay);

    @Query("select i.tripDay.id, count(i) from TripItem i where i.tripDay.trip = :trip group by i.tripDay.id")
    List<Object[]> countGroupByDay(@Param("trip") Trip trip);
    void deleteAllByTripDay(TripDay tripDay);
}
