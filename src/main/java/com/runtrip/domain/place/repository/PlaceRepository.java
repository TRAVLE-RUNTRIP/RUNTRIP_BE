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

	interface Candidate {

		String getPlaceId();

		double getDistanceM();
	}
}
