package com.runtrip.domain.place.repository;

import com.runtrip.domain.place.entity.Place;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, String> {

	// 출발지에서 직선거리 radiusM 안의 코스 후보. "기타"는 코스 후보에서 뺀다
	@Query(value = """
			select p.place_id as placeId,
			       ST_Distance(p.location::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) as distanceM
			from places p
			where p.category <> '기타'
			  and ST_DWithin(p.location::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusM)
			""", nativeQuery = true)
	List<Candidate> findCandidates(@Param("lat") double lat, @Param("lng") double lng,
			@Param("radiusM") double radiusM);

	// 편도 코스 후보: 출발지→장소 + 장소→도착지 직선거리 합이 maxSumM 이하인 타원 범위. "기타"는 뺀다
	@Query(value = """
			select c.placeId, c.toStartM, c.toEndM
			from (select p.place_id as placeId,
			             ST_Distance(p.location::geography, ST_SetSRID(ST_MakePoint(:startLng, :startLat), 4326)::geography) as toStartM,
			             ST_Distance(p.location::geography, ST_SetSRID(ST_MakePoint(:endLng, :endLat), 4326)::geography) as toEndM
			      from places p
			      where p.category <> '기타'
			        and ST_DWithin(p.location::geography, ST_SetSRID(ST_MakePoint(:startLng, :startLat), 4326)::geography, :maxSumM)) c
			where c.toStartM + c.toEndM <= :maxSumM
			""", nativeQuery = true)
	List<OneWayCandidate> findOneWayCandidates(@Param("startLat") double startLat, @Param("startLng") double startLng,
			@Param("endLat") double endLat, @Param("endLng") double endLng, @Param("maxSumM") double maxSumM);

	interface OneWayCandidate {

		String getPlaceId();

		double getToStartM();

		double getToEndM();
	}

	interface Candidate {

		String getPlaceId();

		double getDistanceM();
	}
}
