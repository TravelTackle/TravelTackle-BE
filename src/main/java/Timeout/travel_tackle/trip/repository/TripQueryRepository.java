package Timeout.travel_tackle.trip.repository;

import Timeout.travel_tackle.entity.QTrip;
import Timeout.travel_tackle.entity.QTripDay;
import Timeout.travel_tackle.entity.QTripFeedback;
import Timeout.travel_tackle.entity.QTripFeedbackLike;
import Timeout.travel_tackle.entity.QTripFeedbackRecommendation;
import Timeout.travel_tackle.entity.QTripItem;
import Timeout.travel_tackle.entity.QTripRecord;
import Timeout.travel_tackle.entity.QUser;
import Timeout.travel_tackle.entity.Trip;
import Timeout.travel_tackle.entity.TripDay;
import Timeout.travel_tackle.entity.TripItem;
import Timeout.travel_tackle.trip.dto.FeedSort;
import Timeout.travel_tackle.trip.dto.TripDayResponse;
import Timeout.travel_tackle.trip.dto.TripDetailResponse;
import Timeout.travel_tackle.trip.dto.TripItemResponse;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class TripQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 기간(createdAt 기준, 경계 null 이면 무제한) 안에 만들어진 공개 계획마다 모든 일정의 주소를 돌려준다.
     * 일정이 하나도 없는 계획은 빠지고, 주소가 없는 일정은 null 로 들어간다.
     */
    public Map<UUID, List<String>> findItemAddressesOfPublishedTrips(LocalDateTime from, LocalDateTime to) {
        QTrip qTrip = QTrip.trip;
        QTripDay qDay = QTripDay.tripDay;
        QTripItem qItem = QTripItem.tripItem;

        BooleanBuilder where = new BooleanBuilder(qTrip.published.isTrue());
        if (from != null) {
            where.and(qTrip.createdAt.goe(from));
        }
        if (to != null) {
            where.and(qTrip.createdAt.loe(to));
        }

        List<Tuple> rows = queryFactory
                .select(qTrip.id, qItem.address)
                .from(qItem)
                .join(qItem.tripDay, qDay)
                .join(qDay.trip, qTrip)
                .where(where)
                .fetch();

        Map<UUID, List<String>> addressesByTrip = new LinkedHashMap<>();
        for (Tuple row : rows) {
            addressesByTrip.computeIfAbsent(row.get(qTrip.id), id -> new ArrayList<>()).add(row.get(qItem.address));
        }
        return addressesByTrip;
    }

    /**
     * 여행 상세 조회 — N+1 없이 쿼리 2번으로 전체 일정 로딩
     *   쿼리 1: TripDay 목록 (아이템 없는 날도 포함)
     *   쿼리 2: TripItem 전체 + TripDay 페치조인 (1번에 로딩)
     */
    public TripDetailResponse findDetail(Trip trip) {
        QTripDay qDay = new QTripDay("qDay");
        QTripItem qItem = QTripItem.tripItem;

        List<TripDay> days = queryFactory
                .selectFrom(qDay)
                .where(qDay.trip.eq(trip))
                .orderBy(qDay.dayNumber.asc())
                .fetch();

        List<TripItem> items = queryFactory
                .selectFrom(qItem)
                .join(qItem.tripDay, qDay).fetchJoin()
                .where(qDay.trip.eq(trip))
                .orderBy(qDay.dayNumber.asc(), qItem.orderIndex.asc())
                .fetch();

        Map<UUID, List<TripItem>> itemsByDayId = items.stream()
                .collect(Collectors.groupingBy(i -> i.getTripDay().getId()));

        List<TripDayResponse> dayResponses = days.stream()
                .map(day -> TripDayResponse.of(day,
                        itemsByDayId.getOrDefault(day.getId(), List.of()).stream()
                                .map(TripItemResponse::from).toList()))
                .toList();

        return TripDetailResponse.of(trip, dayResponses);
    }

    /**
     * 공개 피드 키워드 검색 — 계획 제목(Trip.title), 기록 제목/내용(TripRecord),
     * 또는 계획에 담긴 각 장소 이름(TripItem.cachedTitle)에 매칭되는 Trip을 조회.
     * RELEVANCE 정렬: 제목매치(랭크 3) > 기록매치(랭크 2) > 장소이름매치(랭크 1), 동점은 최신순.
     * POPULAR로 검색할 땐 참견 수 서브쿼리 정렬이 부정확해지므로 RELEVANCE로 대체한다.
     */
    public Page<Trip> searchPublishedTrips(String keyword, FeedSort sort, Pageable pageable) {
        QTrip qTrip = QTrip.trip;
        QTripRecord qRecord = QTripRecord.tripRecord;
        QUser qUser = QUser.user;
        QTripDay qSearchDay = new QTripDay("qSearchDay");
        QTripItem qSearchItem = new QTripItem("qSearchItem");

        String pattern = "%" + keyword.toLowerCase() + "%";

        BooleanExpression titleMatch = qTrip.title.lower().like(pattern);
        BooleanExpression recordMatch = JPAExpressions.selectOne()
                .from(qRecord)
                .where(qRecord.trip.eq(qTrip)
                        .and(qRecord.title.lower().like(pattern)
                                .or(qRecord.content.lower().like(pattern))))
                .exists();
        BooleanExpression itemMatch = JPAExpressions.selectOne()
                .from(qSearchItem)
                .join(qSearchItem.tripDay, qSearchDay)
                .where(qSearchDay.trip.eq(qTrip)
                        .and(qSearchItem.cachedTitle.lower().like(pattern)))
                .exists();
        BooleanExpression matched = titleMatch.or(recordMatch).or(itemMatch);

        NumberExpression<Integer> rank = new CaseBuilder()
                .when(titleMatch).then(3)
                .when(recordMatch).then(2)
                .when(itemMatch).then(1)
                .otherwise(0);

        OrderSpecifier<?>[] orderSpecifiers = switch (sort) {
            case OLDEST -> new OrderSpecifier<?>[]{qTrip.createdAt.asc()};
            case LATEST -> new OrderSpecifier<?>[]{qTrip.createdAt.desc()};
            default -> new OrderSpecifier<?>[]{rank.desc(), qTrip.createdAt.desc()}; // RELEVANCE, POPULAR
        };

        List<Trip> content = queryFactory
                .selectFrom(qTrip)
                .join(qTrip.user, qUser).fetchJoin()
                .where(qTrip.published.isTrue(), matched)
                .orderBy(orderSpecifiers)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(qTrip.count())
                .from(qTrip)
                .where(qTrip.published.isTrue(), matched)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    /**
     * 여행 전체 삭제 — deleteTrip()에서 호출.
     * 피드백 추천 → 피드백 → 아이템 → 일차 순으로 삭제 (FK 제약 준수)
     */
    public void bulkDeleteByTrip(Trip trip) {
        QTripDay qDay = new QTripDay("qDay");
        QTripItem qItem = QTripItem.tripItem;
        QTripFeedback qFeedback = QTripFeedback.tripFeedback;
        QTripFeedbackRecommendation qRec = QTripFeedbackRecommendation.tripFeedbackRecommendation;
        QTripFeedbackLike qLike = QTripFeedbackLike.tripFeedbackLike;

        queryFactory.delete(qLike)
                .where(qLike.feedback.in(
                        JPAExpressions.selectFrom(qFeedback).where(qFeedback.trip.eq(trip))
                ))
                .execute();

        queryFactory.delete(qRec)
                .where(qRec.feedback.in(
                        JPAExpressions.selectFrom(qFeedback).where(qFeedback.trip.eq(trip))
                ))
                .execute();

        queryFactory.delete(qFeedback)
                .where(qFeedback.trip.eq(trip))
                .execute();

        queryFactory.delete(qItem)
                .where(qItem.tripDay.in(
                        JPAExpressions.selectFrom(qDay).where(qDay.trip.eq(trip))
                ))
                .execute();

        queryFactory.delete(qDay)
                .where(qDay.trip.eq(trip))
                .execute();
    }

    /**
     * 날짜 변경 시 일차·아이템만 삭제. 피드백은 건드리지 않는다.
     * (피드백 day/item 참조는 bulkNullifyFeedbackReferences로 먼저 끊어둔다)
     */
    public void bulkDeleteDaysAndItems(Trip trip) {
        QTripDay qDay = new QTripDay("qDay");
        QTripItem qItem = QTripItem.tripItem;

        queryFactory.delete(qItem)
                .where(qItem.tripDay.in(
                        JPAExpressions.selectFrom(qDay).where(qDay.trip.eq(trip))
                ))
                .execute();

        queryFactory.delete(qDay)
                .where(qDay.trip.eq(trip))
                .execute();
    }

    /**
     * 날짜 변경으로 일차·아이템을 초기화할 때 피드백의 day/item 참조를 null로 처리.
     * 피드백 텍스트는 보존하고 참조만 끊는다.
     */
    public void bulkNullifyFeedbackReferences(Trip trip) {
        QTripFeedback qFeedback = QTripFeedback.tripFeedback;
        QTripDay qDay = new QTripDay("qDay");

        queryFactory.update(qFeedback)
                .setNull(qFeedback.tripItem)
                .where(qFeedback.trip.eq(trip), qFeedback.tripItem.isNotNull())
                .execute();

        queryFactory.update(qFeedback)
                .setNull(qFeedback.tripDay)
                .where(qFeedback.trip.eq(trip), qFeedback.tripDay.isNotNull())
                .execute();
    }
}
