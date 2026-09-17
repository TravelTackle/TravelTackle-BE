package Timeout.travel_tackle.trip.repository;

import Timeout.travel_tackle.entity.TripPhoto;
import Timeout.travel_tackle.entity.TripRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TripPhotoRepository extends JpaRepository<TripPhoto, UUID> {
    List<TripPhoto> findAllByRecordOrderByUploadedAtAsc(TripRecord record);

    void deleteAllByRecord(TripRecord record);

    /**
     * 피드 썸네일용 — 여러 계획의 기록 사진을 업로드순으로 (계획ID, 이미지URL) 형태로 한 번에 조회.
     */
    @Query("select r.trip.id, p.imageUrl from TripPhoto p join p.record r "
            + "where r.trip.id in :tripIds order by p.uploadedAt asc")
    List<Object[]> findThumbnailRowsByTripIds(@Param("tripIds") Collection<UUID> tripIds);
}
