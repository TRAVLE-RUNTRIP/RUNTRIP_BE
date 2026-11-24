package com.runtrip.domain.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

// API 명세 5번 응답. region(BE-07), safety(BE-13), reason(BE-12)은 아직 null
public record CourseResponse(
		@Schema(description = "코스 ID", example = "101") Long courseId,
		@Schema(description = "코스 이름", example = "덕수궁·청계천 코스") String name,
		@Schema(description = "LOOP, ONE_WAY, FREE", example = "LOOP") String type,
		@Schema(description = "실제 경로 거리 (km)", example = "5.2") BigDecimal distanceKm,
		@Schema(description = "예상 시간 (분). 거리 × 6분", example = "31") int estimatedMinutes,
		Coordinate start,
		Coordinate end,
		@Schema(description = "지역", example = "서울 중구", nullable = true) String region,
		@Schema(description = "취향", example = "[\"NIGHT_VIEW\", \"NATURE\"]") List<Preference> preferences,
		@Schema(description = "경로선 [위도, 경도] 배열") List<double[]> path,
		List<CoursePlaceResponse> places,
		@Schema(description = "안전 정보", nullable = true) Object safety,
		@Schema(description = "저장 여부", example = "false") boolean saved,
		@Schema(description = "저장 시각", nullable = true) OffsetDateTime savedAt
) {

	public record CoursePlaceResponse(
			@Schema(description = "장소 ID", example = "T1116925") String placeId,
			@Schema(description = "방문 순서", example = "1") int order,
			@Schema(description = "장소 이름", example = "덕수궁 돌담길") String name,
			@Schema(description = "분류", example = "관광 명소") String category,
			@Schema(description = "장소 위도", example = "37.5659") double lat,
			@Schema(description = "장소 경도", example = "126.9752") double lng,
			@Schema(description = "경로에 연결된 지점 위도 (OSRM 스냅)", example = "37.566") double routeLat,
			@Schema(description = "경로에 연결된 지점 경도 (OSRM 스냅)", example = "126.9753") double routeLng,
			@Schema(description = "장소 소개", nullable = true) String overview,
			@Schema(description = "사진 주소", nullable = true) String imageUrl,
			@Schema(description = "사진 출처", nullable = true) String imageCredit,
			@Schema(description = "추천 이유. 생성 직후에는 null", nullable = true) String reason
	) {
	}
}
