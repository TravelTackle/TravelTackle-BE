package Timeout.travel_tackle.entity.Enum;

/**
 * 피드 카드 종류이자, 스크랩(SavedTrip)이 어느 카드에서 눌렸는지(sourceType)도 같은 값으로 표현한다.
 * 한 Trip은 PLAN 카드 1개(항상)와, 기록(TripRecord)이 있으면 RECORD 카드 1개를 별도 항목으로 낸다.
 */
public enum FeedItemType {
    PLAN,
    RECORD
}
