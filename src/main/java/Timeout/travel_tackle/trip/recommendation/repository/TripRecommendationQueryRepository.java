package Timeout.travel_tackle.trip.recommendation.repository;

import Timeout.travel_tackle.entity.QTrip;
import Timeout.travel_tackle.entity.QTripDay;
import Timeout.travel_tackle.entity.QTripItem;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TripRecommendationQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 공개된(본인 제외) 계획에 담긴 방문지의 취향 매칭 신호를 한 번에 조회한다.
     * 행 = (계획ID, 대분류, 중분류, 지역코드). 점수 계산은 서비스에서 in-memory로 수행.
     */
    public List<ItemSignal> findPublishedItemSignals(UUID excludeUserId) {
        QTripItem item = QTripItem.tripItem;
        QTripDay day = QTripDay.tripDay;
        QTrip trip = QTrip.trip;

        return queryFactory
                .select(Projections.constructor(ItemSignal.class,
                        trip.id, item.lclsSystm1, item.lclsSystm2, item.regionCode))
                .from(item)
                .join(item.tripDay, day)
                .join(day.trip, trip)
                .where(trip.published.isTrue(), trip.user.id.ne(excludeUserId))
                .fetch();
    }

    public record ItemSignal(UUID tripId, String lclsSystm1, String lclsSystm2, String regionCode) {
    }
}
