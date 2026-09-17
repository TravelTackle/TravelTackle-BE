package Timeout.travel_tackle.trip.repository;

import Timeout.travel_tackle.entity.Trip;
import Timeout.travel_tackle.entity.TripDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TripDayRepository extends JpaRepository<TripDay, UUID> {
    List<TripDay> findAllByTripOrderByDayNumber(Trip trip);
    void deleteAllByTrip(Trip trip);
}
